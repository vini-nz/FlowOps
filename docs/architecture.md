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

Pelo mesmo motivo, `BudgetService` grava seus próprios eventos
(`ORCAMENTO_CRIADO`, `ITEM_ADICIONADO`, `ITEM_REMOVIDO`,
`ORCAMENTO_APROVADO`/`ORCAMENTO_RECUSADO`) com um `recordEvent` privado
duplicado do mesmo método em `WorkOrderService`, em vez de extrair um
serviço de eventos compartilhado agora — essa extração pertence ao item
"Padronização Arquitetural" do Épico Dívida Técnica, que ainda não rodou;
duplicar um helper de 10 linhas é mais barato que introduzir uma abstração
nova no meio de uma feature que não é sobre isso.

## Itens de orçamento gravam snapshot, não referência viva ao catálogo

`BudgetItem.description`/`unitPrice` são copiados do `CatalogItem` no
momento da adição (`BudgetService.addItem`), não recalculados a partir de
`catalog_item_id` na leitura. Editar o preço de um item de catálogo depois
não deve alterar orçamentos já criados — é o mesmo raciocínio de qualquer
sistema de nota fiscal/pedido: o valor cobrado é o que foi acordado, não o
preço de tabela atual. `catalog_item_id` é mantido (com `ON DELETE SET
NULL`) só para rastreabilidade — "de qual item de catálogo isso veio" —
nunca para recalcular valores.

