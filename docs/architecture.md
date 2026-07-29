# Decisões de arquitetura

Este documento reúne as escolhas técnicas do backend que valem a pena saber
explicar — o "porquê", não só o "o quê". A documentação completa de domínio
de negócio, casos de uso e modelo de dados fica no Notion do projeto; aqui
ficam apenas as decisões de implementação.

## Timestamps controlados pelo banco, não pelo Java

As colunas `created_at`/`updated_at` são mapeadas como
`insertable = false, updatable = false` nas entidades JPA. O valor real vem
do `DEFAULT now()` e do trigger `set_updated_at()` do PostgreSQL, definidos
no schema (`db/flowops_ddl.sql`) — nunca de lógica duplicada na aplicação.
Isso evita divergência entre o horário do banco e o do servidor de aplicação,
e garante que qualquer UPDATE feito fora da aplicação (uma migração manual,
por exemplo) também atualiza `updated_at` corretamente.

## UUID gerado no Java, não no banco

Embora a coluna tenha `DEFAULT gen_random_uuid()`, o `@PrePersist` gera o
UUID antes do insert, para que o objeto já tenha o valor correto em memória
sem precisar de um `refresh()` depois de salvar.

## IDs internos nunca são expostos na API

O `id` sequencial (`BIGSERIAL`) é usado apenas internamente, para joins e
índices. Toda resposta da API e toda rota que recebe um identificador do
cliente HTTP usa o `uuid` — não expor sequências previsíveis é uma prática
básica de segurança, e vale mais a pena aplicá-la desde o primeiro módulo do
que corrigir depois.

## JWT stateless com claims mínimos

O payload carrega apenas `userId`, `companyId` e `role` — o suficiente para
autorização, sem inflar o token com dados que mudam com frequência (isso
ficaria desatualizado até o token expirar, já que o token não é validado
contra o banco a cada requisição).

## UserDetails implementado diretamente na entidade User

Evita uma classe adaptadora extra. Para o tamanho atual do domínio, o
acoplamento entre entidade JPA e o contrato do Spring Security é aceitável;
a alternativa (um DTO de segurança separado, hidratado a partir do usuário)
vale a pena revisitar se o modelo de usuário crescer muito.

## `ddl-auto: validate`, nunca `update` ou `create`

O schema é responsabilidade exclusiva do `flowops_ddl.sql`. O Hibernate
apenas confirma que o mapeamento das entidades bate com as tabelas
existentes — se alguém alterar uma entidade sem atualizar o DDL, a aplicação
falha ao iniciar, em vez de alterar o banco silenciosamente em produção.

## Carregamento de relacionamentos: LAZY por padrão, EntityGraph quando necessário

Todo `@ManyToOne` é `LAZY` por padrão — evita carregar dados que a maioria
das consultas não precisa. Onde um caminho de código específico precisa do
relacionamento carregado fora de uma transação (como o filtro de
autenticação JWT, que roda antes de qualquer `@Transactional`), a solução é
um `@EntityGraph` no método do repositório usado por aquele caminho, em vez
de tornar o relacionamento `EAGER` globalmente — isso evita penalizar todas
as outras consultas que não precisam do dado extra.

## Exclusão inteligente: hard delete quando possível, soft delete quando necessário

`ClientService.deactivate` decide entre remover um cliente de verdade
(`DELETE`) ou apenas desativá-lo (`deleted_at`), com base em uma checagem
explícita (`WorkOrderRepository.existsByClientId`) — nunca tentando o
`DELETE` e reagindo a uma exceção de violação de FK como controle de fluxo.

Isso importa por um motivo concreto, não só estético: no PostgreSQL, uma
transação que sofre qualquer erro fica **abortada por completo** até o fim
do bloco — uma tentativa de `DELETE` que falha por violação de FK impede
qualquer comando seguinte na mesma transação, incluindo o `UPDATE` de soft
delete que tentaria vir logo depois. Ou seja, "tenta deletar, se der erro
faz soft delete" não funciona dentro de uma única transação sem uma segunda
transação isolada — e o `existsBy` explícito evita essa complicação
inteiramente, decidindo antes de qualquer escrita no banco.

A constraint de FK (`work_orders.client_id REFERENCES clients(id)`)
continua existindo como rede de segurança: se uma violação chegar mesmo
assim (uma tabela nova referenciando `clients` sem checagem explícita
atualizada, por exemplo), o `GlobalExceptionHandler` responde com 409 em
vez de deixar vazar um 500.

## Email de usuário é único globalmente, não por empresa

A constraint original era `UNIQUE(company_id, email)`, permitindo o mesmo
e-mail em empresas diferentes. Isso quebrava o login: `UserRepository.
findByEmailAndActiveTrue` espera uma única linha, e com dois usuários ativos
compartilhando e-mail em empresas diferentes a consulta retornava duas,
lançando `IncorrectResultSizeDataAccessException` (500).

A correção (`UNIQUE(email)` global) resolve o problema para o modelo atual,
onde cada usuário pertence a exatamente uma empresa. Ver
[`docs/adr/0001-modelo-de-usuario.md`](adr/0001-modelo-de-usuario.md) para a
decisão consciente de manter esse modelo e não migrar para uma identidade de
usuário global neste momento do projeto.

## Isolamento multi-tenant reforçado em toda consulta por identificador

Nenhuma consulta que recebe um identificador vindo de fora usa `findById()`
puro. Toda busca por um registro específico (cliente, WorkOrder) filtra
também por `company_id` do usuário autenticado — sem isso, bastaria
adivinhar ou enumerar um UUID para ler ou alterar dados de outra empresa.

## State machine da WorkOrder isolada em sua própria classe

`WorkOrderStatusTransitions` não depende de Spring, JPA ou qualquer
framework — só do enum `WorkOrderStatus` e coleções puras do Java. Isso é
deliberado: a regra "quais transições de status fazem sentido" é uma regra
de negócio, não um detalhe de persistência, e mantê-la isolada permite
testá-la com um `javac` simples, sem subir um contexto Spring inteiro. Os
28 cenários de transição (caminho feliz completo, estados terminais, pulos
de etapa, retrocessos) foram testados exatamente assim antes da entrega.

`WorkOrderService.updateStatus` consulta essa classe antes de qualquer
escrita no banco; uma transição inválida nunca chega a tocar o banco de
dados, e retorna 409 com a transição exata que foi rejeitada.

## RBAC aplicado via `@PreAuthorize`, não apenas documentado

Desde a Sprint 1 existe uma matriz de permissões documentada (Negócio e
Domínio, Notion) definindo, por exemplo, que apenas `ADMIN_EMPRESA` e
`OPERADOR` podem criar WorkOrders. Até a Sprint 2, essa regra existia só
como documentação — qualquer usuário autenticado, de qualquer role, podia
chamar qualquer endpoint. A partir da Sprint 3, `@EnableMethodSecurity` foi
habilitado no `SecurityConfig` e os endpoints de criação, transição de
status e atribuição de responsável em WorkOrders usam
`@PreAuthorize("hasAnyRole('ADMIN_EMPRESA', 'OPERADOR')")`. Uma tentativa de
acesso sem o role exigido retorna 403 (`AccessDeniedException`, tratada
explicitamente no `GlobalExceptionHandler` — sem esse handler, cairia no
catch-all genérico e voltaria como 500, o que esconderia a causa real).

## State machine da Etapa: self-transition deliberadamente permitida

`StepStatusTransitions` (Sprint 4) segue o mesmo princípio de
`WorkOrderStatusTransitions` — isolada de Spring/JPA, testável com `javac`
puro (21 cenários) — mas com uma diferença consciente: `isValid(x, x)` é
`true` para todo status não-terminal. A WorkOrder nunca permite isso
(`isValid(SOLICITACAO_RECEBIDA, SOLICITACAO_RECEBIDA)` é `false`), mas a
Etapa precisa, porque o único endpoint de escrita
(`PATCH /work-orders/{uuid}/steps/{stepUuid}/status`) cobre tanto "avançar
status" (CU-019/CU-020) quanto "registrar observação" (CU-022) — sem a
self-transition, seria impossível salvar uma nota em uma etapa sem também
mudar seu status. `CONCLUIDA` continua terminal mesmo para si mesma: uma
etapa concluída não se "atualiza" mais.

## Etapas instanciadas a partir do workflow padrão da empresa, na criação da WorkOrder

`WorkOrderService.create` busca o `workflow_template` com `is_default = true`
da empresa (`WorkflowTemplateRepository.findByCompanyIdAndIsDefaultTrue`) e,
se existir, copia cada `workflow_step` do molde para uma `work_order_step` da
WorkOrder recém-criada (Fluxo 3 — Planejamento, Negócio e Domínio no Notion).
Se a empresa não tiver um template padrão configurado, a WorkOrder nasce sem
etapas — estado válido, não um erro; `GET /work-orders/{uuid}/steps` apenas
retorna uma lista vazia. Essa decisão mantém o Workflow "configurável por
empresa" (conforme documentado) sem exigir que toda empresa tenha um
workflow definido para usar o resto do sistema.

## `work_order_steps` ganhou `uuid` na Sprint 4

O DDL original da Etapa 2.2 não previa uma coluna `uuid` em
`work_order_steps` — só o `id` sequencial. Isso quebraria a regra "IDs
internos nunca são expostos na API" (ver acima) assim que a Sprint 4
precisasse expor um identificador de etapa em `WorkOrderStepResponse` e nas
rotas `PATCH .../steps/{stepUuid}/status`. A alternativa seria usar
`step_order` (único por WorkOrder) como identificador de rota, mas isso
criaria uma exceção só para este módulo, divergindo do padrão usado em todo
o resto do sistema (`Client`, `WorkOrder`). Optou-se por adicionar a coluna
`uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid()`, consistente com as
demais tabelas.

## Dashboard migrado para `DashboardService` na Sprint 4

Da Sprint 1 até a Sprint 3, a lógica do resumo operacional vivia direto no
`DashboardController` — aceitável como "endpoint mínimo antecipado", prova
de leitura protegida do banco. A partir da Sprint 4, com WorkOrders recentes
e próximas entregas somando-se aos contadores por status, o Dashboard passou
a seguir o mesmo padrão Controller → Service → Repository do resto do
projeto (`DashboardService`, `@Transactional(readOnly = true)`).

As duas novas consultas (`findTop5By...OrderByCreatedAtDesc` e
`findTop5By...ScheduledEndGreaterThanEqualAndStatusNotInOrderByScheduledEndAsc`)
usam `@EntityGraph(attributePaths = "client")`, pelo mesmo motivo documentado
acima para `WorkOrderRepository`: `DashboardWorkOrderItem.from()` acessa
`wo.getClient().getName()` fora de qualquer transação de Service explícita
(o Controller delega para o Service, mas a serialização do DTO acontece
depois que o método `@Transactional` já retornou) — sem o `EntityGraph`,
seria o mesmo `LazyInitializationException` da Sprint 1, só que em um
caminho de código novo.

## Bug encontrado na Sprint 4: `401` vs `403` em rotas sem token

Testando os requisitos das Sprints 1 a 3 durante esta entrega, `GET
/auth/me` sem header `Authorization` retornava `403`, não o `401`
documentado desde sempre em `docs/api.md`. Causa raiz: o projeto usa só JWT
stateless — login por formulário e HTTP Basic nunca foram habilitados no
`SecurityConfig`. Sem um `AuthenticationEntryPoint` customizado registrado,
o Spring Security cai no fallback padrão para esse cenário,
`Http403ForbiddenEntryPoint`. O comentário em `JwtAuthenticationFilter` ("o
SecurityConfig vai bloquear com 401") assumia um comportamento que nunca foi
configurado explicitamente.

Corrigido com `RestAuthenticationEntryPoint`, registrado via
`.exceptionHandling(ex -> ex.authenticationEntryPoint(...))` no
`SecurityConfig`, devolvendo o mesmo formato de erro do
`GlobalExceptionHandler` (`status`/`error`/`message`/`timestamp`) com `401`.

## Orçamento reaproveita `WorkOrderStatusTransitions` em vez de duplicar a state machine (V2.1)

`BudgetService` não atribui `WorkOrder.status` diretamente em nenhum ponto.
Toda transição (criação do orçamento move `SOLICITACAO_RECEBIDA →
ORCAMENTO_GERADO`; decisão move `ORCAMENTO_GERADO → AGUARDANDO_APROVACAO →
APROVADO|RECUSADO`) passa por `WorkOrderService.updateStatus`, o mesmo
método já usado pelo `WorkOrderController`. A alternativa seria o
`BudgetService` validar e persistir o novo status por conta própria — mais
rápido de escrever, mas criaria uma segunda cópia da validação de
`WorkOrderStatusTransitions` (D-02) fora do lugar onde ela já é testada e
mantida, com risco real de as duas divergirem com o tempo. O custo aceito é
uma query extra por transição (o `WorkOrder` é recarregado dentro de
`updateStatus`), irrelevante no volume atual.

`BudgetService` também grava seus próprios eventos (`ORCAMENTO_CRIADO`,
`ITEM_ADICIONADO`, `ITEM_REMOVIDO`, `ORCAMENTO_APROVADO`/
`ORCAMENTO_RECUSADO`).

> **Atualizado na V2.7:** na V2.1 cada serviço fazia isso com um
> `recordEvent` privado duplicado, decisão consciente na época — duplicar um
> helper de 10 linhas era mais barato que introduzir uma abstração no meio de
> uma feature que não era sobre isso. Notificações mudaram o cálculo e a
> extração aconteceu; hoje todos usam `DomainEventService` (ver seção "Um só
> ponto grava evento" adiante).

## Itens de orçamento gravam snapshot, não referência viva ao catálogo

`BudgetItem.description`/`unitPrice` são copiados do `CatalogItem` no
momento da adição (`BudgetService.addItem`), não recalculados a partir de
`catalog_item_id` na leitura. Editar o preço de um item de catálogo depois
não deve alterar orçamentos já criados — é o mesmo raciocínio de qualquer
sistema de nota fiscal/pedido: o valor cobrado é o que foi acordado, não o
preço de tabela atual. `catalog_item_id` é mantido (com `ON DELETE SET
NULL`) só para rastreabilidade — "de qual item de catálogo isso veio" —
nunca para recalcular valores.

## Contrato de `domain_events.payload` (V2.3)

Até a V2.2 o payload era gravado por três Services sem contrato escrito —
item registrado no Épico Dívida Técnica com a nota "documentar antes de
Notificações/Automações dependerem dele". A Timeline é o primeiro consumidor
real, então o contrato passa a valer a partir daqui:

| `event_type` | `payload` | Gravado por |
|---|---|---|
| `WORKORDER_CRIADA` | `null` | `WorkOrderService.create` |
| `STATUS_ALTERADO` | `{"de": "<WorkOrderStatus>", "para": "<WorkOrderStatus>"}` | `WorkOrderService.updateStatus` |
| `RESPONSAVEL_ATRIBUIDO` | `{"assignedTo": "<nome>"}` ou `{"assignedTo": null}` | `WorkOrderService.assign` |
| `ETAPA_STATUS_ALTERADA` | `{"etapa": "<título>", "de": "<StepStatus>", "para": "<StepStatus>"}` | `WorkOrderStepService.updateStatus` |
| `ETAPA_OBSERVACAO_REGISTRADA` | `{"etapa": "<título>"}` | `WorkOrderStepService.updateStatus` |
| `ORCAMENTO_CRIADO` | `null` | `BudgetService.create` |
| `ITEM_ADICIONADO` | `{"description": "<descrição>", "subtotal": <número>}` | `BudgetService.addItem` |
| `ITEM_REMOVIDO` | `{"description": "<descrição>"}` | `BudgetService.removeItem` |
| `ORCAMENTO_APROVADO` / `ORCAMENTO_RECUSADO` | `null` | `BudgetService.updateStatus` |

`TimelineDescriptionFormatter` é o **único** ponto do sistema que lê esse
payload. Consumidores futuros (Notificações, Automações) devem reusá-lo em
vez de reimplementar a leitura — duas interpretações do mesmo payload
divergem com o tempo e o erro só aparece na tela do usuário.
`TimelineDescriptionFormatterTest` é a validação executável deste contrato:
mudar o formato do payload em um Service sem atualizar o formatter quebra o
teste. Tipo desconhecido ou payload malformado cai em fallback (nunca lança),
porque um evento de auditoria antigo não pode derrubar a tela inteira.

## Ordenação da Timeline desempata por `id`, não só por `occurred_at`

`occurred_at` usa `DEFAULT now()`, e no PostgreSQL `now()` devolve o horário
de **início da transação**, não o do `INSERT`. Vários eventos gravados na
mesma transação recebem timestamps idênticos — `BudgetService.updateStatus`,
por exemplo, grava duas transições de status e o `ORCAMENTO_APROVADO` de uma
vez. Ordenar apenas por `occurred_at` deixava a ordem entre eles indefinida,
e na prática a Timeline exibia a aprovação **antes** das transições que a
causaram. A consulta ordena por `occurred_at, id` (`id` é `BIGSERIAL`,
portanto preserva a ordem real de inserção). Alternativa descartada: trocar o
default para `clock_timestamp()`, que resolveria na origem, mas alteraria o
significado da coluna em dados já gravados e não ajudaria em eventos
inseridos no mesmo microssegundo.

## CSV correto é diferente de CSV válido (V2.9)

A exportação gera CSV em vez de `.xlsx` para não trazer o Apache POI por um
ganho pequeno. A troca é defensável, mas só se o CSV for gerado pensando em
quem vai abri-lo: um arquivo separado por vírgula, em UTF-8 sem BOM e com
ponto decimal é um CSV perfeitamente **válido** — e chega **quebrado** no
Excel em português, de três formas simultâneas:

| Sem tratamento | O que o usuário vê |
|---|---|
| UTF-8 sem BOM | `InstalaÃ§Ã£o` no lugar de `Instalação` |
| Separador vírgula | A planilha inteira numa única coluna |
| `1234.56` | Texto em vez de número; a coluna não soma |

`CsvWriter` resolve os três (BOM, `;`, vírgula decimal) e aplica o escape do
RFC 4180 adaptado ao separador — campo com `;`, aspas ou quebra de linha
entre aspas, aspas internas duplicadas. Sem esse último detalhe, uma
observação como `Entregar; conferir medidas` deslocaria todas as colunas
seguintes daquela linha.

Nada disso aparece lendo o conteúdo do arquivo num editor de texto: o CSV
"errado" parece perfeitamente normal. Por isso os testes verificam os bytes
do BOM e o escape diretamente, e a validação manual re-parseia o arquivo
gerado conferindo a contagem de colunas por linha.

**Efeito colateral descoberto no navegador:** numa requisição CORS o
navegador só entrega ao JavaScript uma lista curta de headers de resposta, e
`Content-Disposition` não está nela — todo download feito via `fetch`/axios
perdia o nome montado pelo backend e caía num nome genérico. Corrigido com
`setExposedHeaders` no `SecurityConfig`. Por `curl` o header sempre esteve
presente, então esse tipo de defeito só aparece exercitando o caminho real do
usuário.

## Troca de senha precisa derrubar sessão, e JWT stateless não faz isso sozinho (V2.8)

O JWT do FlowOps é stateless: o servidor não guarda sessão, só valida
assinatura e expiração. A consequência é que trocar a senha, por si só, não
faz nada com os tokens já emitidos — um token roubado continuaria válido até
expirar (60 min por padrão) mesmo depois de a vítima trocar a senha. Isso
transformaria "trocar senha" num gesto sem efeito de segurança real, que é
justamente o oposto do motivo pelo qual alguém troca a senha.

A solução é uma marca de corte por usuário: `users.password_changed_at`. O
`JwtAuthenticationFilter` recusa qualquer token cujo `iat` seja anterior a
ela. Custo real: **zero query extra** — o filtro já carregava o `User` a cada
requisição para montar o `UserDetails`.

Dois detalhes que a implementação exigiu:

- **A comparação trunca `password_changed_at` para segundos.** O claim `iat`
  do JWT tem precisão de segundo, enquanto a coluna guarda microssegundos.
  Sem truncar, o token novo emitido no mesmo segundo da troca seria recusado
  junto com os antigos, e quem acabou de trocar a senha levaria `401`
  imediatamente.
- **A resposta devolve um token novo.** Quem trocou a senha deve continuar
  logado; só as outras sessões caem. O mesmo vale para troca de e-mail, já
  que o `sub` do JWT é o e-mail e o token antigo deixa de resolver o usuário.

Isso **não** substitui o item "Sessões ativas" da V3 — lá o usuário vê e
revoga sessões individualmente, o que exige registrar cada uma. Aqui é o
mínimo correto para que a troca de senha signifique alguma coisa.

## Um só ponto grava evento, e quem reage é desacoplado (V2.7)

Até a V2.6 cada serviço tinha sua própria cópia privada de `recordEvent` —
quatro implementações idênticas em `WorkOrderService`, `BudgetService`,
`WorkOrderStepService` e `EvidenceService`. Era dívida técnica registrada
desde a V2.1, tolerada enquanto era só duplicação estética. Notificações
mudaram isso: precisariam se plugar nos quatro, e a alternativa era espalhar
a lógica de notificação por todos eles.

`DomainEventService` passou a ser o único ponto que escreve em
`domain_events`. Além de persistir, publica um `WorkOrderEventOccurred`, e
quem quiser reagir se inscreve — hoje só `NotificationListener`.

Três decisões que sustentam isso:

- **O evento publicado carrega valores simples, nunca entidades.** Os
  consumidores rodam em `AFTER_COMMIT`, ou seja, depois que a transação
  fechou: uma entidade LAZY passada ali estaria destacada e qualquer acesso a
  associação estouraria `LazyInitializationException`.
- **`AFTER_COMMIT` e não `AFTER_COMPLETION`.** Se a transação de negócio der
  rollback, nada é notificado — o usuário nunca recebe aviso de algo que não
  aconteceu. O listener abre a própria transação (`REQUIRES_NEW`), já que a
  original terminou.
- **Falha ao notificar é logada e engolida.** Notificação é efeito colateral;
  não pode derrubar — nem desfazer — uma operação de negócio já confirmada no
  banco. Há teste cobrindo exatamente isso.

`TimelineDescriptionFormatter` é reaproveitado para montar a mensagem, o que
mantém a mesma frase na Timeline e na notificação. Ele já tinha sido escrito
prevendo esse segundo consumidor.

## Arquivos ficam fora do banco e fora do backend (V2.6)

Evidências são a primeira funcionalidade que guarda binário. Duas decisões,
detalhadas em `docs/adr/0004-storage-de-evidencias.md`:

**O arquivo nunca entra no banco nem trafega pela API.** `evidences` guarda
só metadado e a chave do objeto; o binário vive num storage S3-compatível
(MinIO local via `docker-compose`, trocável por S3/R2/B2 só por variável de
ambiente). Gravar em pasta local seria pior que parece: em Railway/Render o
disco do contêiner é efêmero e os arquivos sumiriam no próximo deploy.

**A assinatura da URL cobre o host, e isso obriga dois clientes S3.** Dentro
do Docker o backend alcança o storage em `storage:9000`, mas o navegador só
enxerga `localhost:9000`. Como o SigV4 assina o cabeçalho `Host`, uma URL
assinada com o nome interno seria inútil no browser — nem resolve o nome, nem
a assinatura confere. Daí `StorageConfig` expor `S3Client` no endpoint
interno (bucket, `headObject`, exclusão) e `S3Presigner` no endpoint público
(só para assinar).

A consequência de não participar da transferência é que o backend precisa
registrar o metadado **antes** do upload, para poder assinar a URL. Por isso
`uploaded_at` nasce nulo e a evidência só existe para o sistema depois do
`confirm`, que faz `headObject` para comprovar que o objeto chegou — e
regrava o tamanho com o valor real do storage, ignorando o que o cliente
informou. Uploads abandonados deixam linhas pendentes, que nunca aparecem em
listagem (todo `find` filtra `uploaded_at IS NOT NULL`); a rotina de limpeza
periódica é dívida técnica registrada, não esquecimento.

## Workflow é molde, WorkOrder é instância (V2.5)

O schema já previa `workflow_templates`/`workflow_steps` por empresa desde a
Etapa 2.2, e `WorkOrderService.create` já instanciava as etapas a partir do
template padrão desde a Sprint 4 — mas nada no código criava um template.
Só o `seed.sql`. Na prática, o "Workflow é configurável por empresa" que a
documentação de Negócio e Domínio afirmava não existia: uma empresa sem
linha em `workflow_templates` teria WorkOrders nascendo sem nenhuma etapa,
sem qualquer pista do porquê. A V2.5 expôs o CRUD que faltava.

**A relação entre molde e instância é de cópia, não de referência.**
`instantiateSteps` copia `title` e `step_order` para `work_order_steps`, e
`instantiateChecklist` copia `description` e `item_order` para
`work_order_step_checklist_items`. Renomear, reordenar ou excluir algo no
molde depois disso não toca em nenhuma OS já criada — o que é a única
semântica defensável: um Técnico não pode ver mudar, sob os pés dele, o
item que acabou de marcar; e uma OS entregue precisa continuar mostrando o
que foi de fato exigido na época. É o mesmo princípio já aplicado a
`budget_items` na V2.1.

As colunas `workflow_step_id` e `workflow_checklist_item_id` sobrevivem
apenas como rastreabilidade ("de qual molde isto veio"), com
`ON DELETE SET NULL`. Sem isso, a partir do momento em que o Admin pode
excluir uma etapa, qualquer exclusão de etapa já usada falharia por violação
de FK — e excluir um template inteiro cascatearia para suas etapas e bateria
na mesma parede.

Duas consequências desenhadas de propósito:

- **No máximo um template padrão por empresa**, garantido por índice parcial
  (`uq_workflow_templates_single_default`), porque é ele que a criação da
  WorkOrder procura — dois tornariam a escolha arbitrária. Pelo mesmo motivo
  o serviço impede remover o padrão sem eleger outro, e o primeiro template
  criado vira padrão sozinho.
- **Item de checklist vindo do molde não pode ser removido de uma OS.** O
  Técnico pode acrescentar itens avulsos àquela OS (o que o backlog pedia:
  "criar e marcar itens de checklist"), mas apagar um item que a empresa
  definiu esconderia uma exigência — para isso, muda-se o workflow.

## Geração de PDF: OpenPDF em vez de HTML→PDF (V2.2)

`BudgetPdfService` monta o documento diretamente com a API de baixo nível do
OpenPDF (`Document`/`PdfPTable`), não com um template HTML renderizado (ex:
Flying Saucer/Thymeleaf). Alternativa descartada: motor HTML→PDF é mais
confortável para layouts ricos, mas traz uma dependência a mais (parser
HTML/CSS) para um documento de uma página com cabeçalho, uma tabela e um
total — sem justificativa de complexidade para o V2.2. Fica registrado aqui
porque, se um V3 exigir modelos de PDF mais elaborados (ex: com logo da
empresa, múltiplas páginas, cabeçalho/rodapé repetido), vale reavaliar para
um motor baseado em template em vez de continuar montando o documento
imperativamente. Biblioteca escolhida (OpenPDF, LGPL/MPL) em vez de iText
por licenciamento — iText 5+/7 é AGPL, incompatível com uso comercial sem
licença paga.

