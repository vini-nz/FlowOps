# Changelog

Todas as mudanças relevantes do projeto são registradas aqui.
Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

## [V2.7] — Notificações Internas

Último 🔴 Must funcional da V2 (restam apenas Perfil, Exportação CSV/Excel e
Padronização). Fecha também uma dívida técnica registrada desde a V2.1.

### Adicionado
- Notificações in-app geradas a partir de `domain_events`: tabela
  `notifications` por **destinatário** (um mesmo fato pode virar zero ou
  várias notificações, e cada pessoa marca a sua como lida sem afetar as
  demais)
- `NotificationListener` com `@TransactionalEventListener(AFTER_COMMIT)` e
  `REQUIRES_NEW` — se a transação de negócio der rollback, **nada é
  notificado**: ninguém recebe aviso de algo que não aconteceu. Uma falha ao
  notificar é logada mas não propaga, porque notificação é efeito colateral e
  não pode derrubar uma operação já confirmada no banco
- API: listar (paginado), contador de não lidas, marcar uma e marcar todas.
  O destinatário vem sempre do token, nunca de parâmetro da requisição
- Frontend: sino com contador no cabeçalho de todas as telas, painel com as
  10 mais recentes, marcar ao clicar e "marcar todas como lidas"

### Regras de quem é notificado
- Só `STATUS_ALTERADO` e `RESPONSAVEL_ATRIBUIDO` viram notificação. Notificar
  cada item de checklist ou evidência transformaria o sino em ruído e faria o
  usuário parar de olhar — o oposto do objetivo
- O destinatário é o **responsável pela WorkOrder**, e nunca quem provocou o
  evento: avisar alguém da própria ação é ruído puro
- Uma notificação pertence a uma pessoa — aqui o isolamento por empresa não
  basta, e um colega da mesma empresa não lê nem marca a notificação de outro

### Dívida técnica quitada
- **`recordEvent` duplicado em 4 serviços** (WorkOrder, Budget,
  WorkOrderStep, Evidence), registrado em `docs/architecture.md` desde a
  V2.1. Notificações precisariam se plugar em todos eles, o que tornaria a
  duplicação um problema real e não só estético. Extraído para
  `DomainEventService`, único ponto que grava em `domain_events` e publica o
  evento de aplicação

### Corrigido
- **Timeline mostrava enum cru** para os 5 tipos de evento criados na V2.5 e
  V2.6 (`CHECKLIST_ITEM_MARCADO`, `CHECKLIST_ITEM_DESMARCADO`,
  `CHECKLIST_ITEM_ADICIONADO`, `EVIDENCIA_ANEXADA`, `EVIDENCIA_REMOVIDA`) —
  eles caíam no fallback do `TimelineDescriptionFormatter`. Agora aparecem
  como frase legível, ex: *Checklist marcado na etapa "Produção": Conferir
  medidas com o projeto*

### Testado
- 63 cenários no total (`NotificationListenerTest`, 6 novos): notifica o
  responsável, **não** notifica o autor da própria ação, não notifica sem
  responsável, atribuição notifica, eventos de rotina não viram notificação,
  e falha ao notificar não propaga
- Validado contra o sistema real com dois usuários: o Operador movimentou uma
  OS atribuída ao Técnico — o Técnico recebeu 3 notificações com mensagem
  legível e o Operador ficou com 0. Depois o próprio Técnico agiu na sua OS e
  o contador dele não subiu
- Isolamento verificado: o Operador tentando marcar como lida uma notificação
  do Técnico recebe `404`
- Sino, contador e marcação validados pelo navegador, sem erro de console

## [V2.6] — Evidências por Etapa

Fecha o item 3 do Backlog Detalhado, cuja parte de checklist saiu na V2.5.
Primeira funcionalidade do projeto que guarda arquivo binário. Ver ADR-0004.

### Adicionado
- Storage S3-compatível com **MinIO no `docker-compose`**: o projeto continua
  subindo inteiro com um comando, sem exigir conta em provedor de nuvem. Como
  o código usa o SDK oficial da AWS, migrar para S3/Cloudflare R2/Backblaze em
  produção é troca de variável de ambiente — nenhuma linha de código muda
- Upload por **URL pré-assinada**: o arquivo vai do navegador direto ao
  storage, sem passar pelo backend. Fluxo em três passos —
  `POST /evidences/upload-url` (valida e assina) → `PUT` direto no storage →
  `POST /evidences/{uuid}/confirm` (verifica que o objeto chegou)
- Galeria por etapa com download por URL assinada de curta duração: o bucket
  é privado, nada é servido publicamente, e cada acesso passa por uma URL que
  o backend só emite após checar o isolamento por empresa
- Validação de tipo (JPEG, PNG, WebP, HEIC, PDF) e tamanho (15 MB por
  padrão, configurável) **antes** de emitir a URL
- Bucket criado automaticamente no boot, se não existir — sem isso o primeiro
  upload de uma instalação nova falharia com correção manual no console
- Frontend: anexar, listar, baixar e remover evidências no painel de etapas
- Aviso (não trava) de itens de checklist pendentes ao trabalhar uma etapa —
  ponto que ficou em aberto na V2.5

### Detalhe técnico que a URL pré-assinada exigiu
- A assinatura SigV4 cobre o cabeçalho `Host`. Dentro do Docker o backend
  fala com `storage:9000`, mas o navegador só alcança `localhost:9000` —
  assinar com o host interno geraria URL inútil no browser. Por isso há dois
  clientes: `S3Client` no endpoint interno (bucket, `headObject`, exclusão) e
  `S3Presigner` no endpoint público (só para assinar). `pathStyleAccess` é
  obrigatório para MinIO
- No frontend, o `PUT` no storage usa `fetch` puro e **não** o axios da API:
  o interceptor dela anexa `Authorization`, que não faz parte da assinatura e
  faria o storage recusar o envio

### Dívida técnica registrada
- **Registros órfãos:** como o backend não presencia a transferência, o
  metadado nasce com `uploaded_at` nulo e só vira evidência de verdade após a
  confirmação. Upload abandonado deixa linha pendente. Listagens filtram
  `uploaded_at IS NOT NULL` (órfão nunca aparece nem é baixável) e existe
  índice parcial para varrê-los, mas **a rotina de limpeza periódica não foi
  implementada** — custo desprezível, o objeto sequer chegou ao storage

### Testado
- 57 cenários no total (`EvidenceServiceTest`, 9 novos): tipo e tamanho
  recusados antes de assinar, OS não aprovada, etapa concluída, confirmação
  de upload que nunca chegou, uso do tamanho real do storage em vez do
  informado pelo cliente, e evidência de outra etapa não resolvendo
- Ciclo real validado ponta a ponta: PNG enviado por URL pré-assinada,
  baixado de volta e comparado byte a byte com o original (idêntico), e
  arquivos conferidos fisicamente no MinIO com as chaves organizadas por
  empresa/OS/etapa
- Upload **pelo navegador** validado (exercita o CORS do `PUT` de verdade),
  sem erro de console
- Isolamento multi-tenant: criei uma segunda empresa e todas as rotas de
  evidência responderam `404` para ela — listar, baixar, excluir e pedir
  upload — sem vazar sequer a existência do recurso

## [V2.5] — Workflow configurável e Checklist por Etapa

Antecipa para a V2 o "Definir template de workflow padrão" que estava em
"Configurações da Empresa" (V4, 🟡 Could). Motivo: não era só flexibilidade —
sem isso o módulo de Etapas não funcionava para nenhuma empresa que não fosse
a de demonstração, e o checklist do backlog já era especificado como
"configurável por template de etapa", ou seja, dependia disto.

### Corrigido
- **Empresa sem workflow nunca teria etapa nenhuma.** Nada no código criava
  um `workflow_template` — só o `seed.sql`. `WorkOrderService.create` procura
  o template padrão, não acha, e a WorkOrder nasce sem etapas, para sempre.
  O módulo de Etapas só funcionava porque o seed inseria "Padrão Marcenaria"
  na mão. Agora o Admin cria o seu, e o primeiro template da empresa vira o
  padrão automaticamente
- **Excluir uma etapa de molde já usada quebrava com violação de FK.**
  `work_order_steps.workflow_step_id` não tinha cláusula `ON DELETE`, então
  qualquer exclusão de etapa (ou do template inteiro, via CASCADE) falhava se
  alguma OS já a tivesse usado. Passa a ser `ON DELETE SET NULL`, mesma
  solução aplicada a `budget_items.catalog_item_id` na V2.1 — a OS mantém
  título e ordem (cópias próprias) e perde só o ponteiro de origem

### Adicionado
- Módulo de Workflow (`WorkflowController`, `WorkflowService`): Admin Empresa
  cria, renomeia e exclui templates; adiciona, renomeia, reordena e remove
  etapas; define os itens de checklist de cada etapa. Leitura liberada aos
  demais papéis (Operador e Técnico precisam saber quais etapas existem),
  escrita restrita a `ADMIN_EMPRESA`
- Checklist por etapa em duas camadas: `workflow_step_checklist_items`
  (molde) é **copiado** para `work_order_step_checklist_items` na criação da
  WorkOrder. O Técnico marca/desmarca itens (com registro de quem e quando) e
  pode acrescentar itens avulsos àquela OS — itens vindos do molde não podem
  ser removidos de uma OS específica, para não esconder uma exigência que a
  empresa definiu
- `workflow_templates` e `workflow_steps` ganharam `uuid`: passaram a ser
  expostos na API e a regra do projeto é que id sequencial nunca sai do
  backend (mesma situação de `work_order_steps` na Sprint 4)
- Índice parcial `uq_workflow_templates_single_default`: no máximo um
  template padrão por empresa, já que é ele que a criação da WorkOrder
  procura — dois tornariam a escolha arbitrária
- Frontend: página `Workflow.jsx` (edição para Admin, somente-leitura com
  aviso para os demais) e checklist no painel de etapas em `WorkOrders.jsx`,
  com marcação, item avulso e indicação de quem marcou

### Garantia mantida
- Editar um molde **não** altera Ordens de Serviço já criadas. Etapas e
  checklist são copiados na criação (`instantiateSteps` /
  `instantiateChecklist`), mesmo princípio de snapshot de `budget_items`.
  Verificado contra o sistema real: renomear uma etapa e excluir outra do
  molde deixou a OS existente intacta, inclusive com a etapa que sumiu do
  molde
- Checklist de etapa `CONCLUIDA` não pode mais ser alterado, e nada de
  checklist é editável fora de `APROVADO`/`EM_EXECUCAO` — as mesmas travas da
  V2.4 valem aqui

### Testado
- 48 cenários no total (13 em `WorkOrderStepServiceTest`, +6 novos de
  checklist: etapa concluída, antes da aprovação, registro de quem/quando ao
  marcar e desmarcar, e a distinção entre item de molde e avulso na remoção)
- Ciclo completo validado contra o sistema real: admin monta um workflow de
  assistência técnica do zero (4 etapas + checklist), reordena, torna padrão,
  uma OS nova nasce com essas etapas, e o Técnico marca itens e acrescenta um
  avulso — tudo também pela interface, sem erro de console

## [V2.4] — Integridade do Fluxo

Entrega não planejada no Kanban original: nasceu de uma revisão do fluxo
ponta a ponta que encontrou estados que o negócio não reconhece. Ver
ADR-0003.

### Corrigido
- **Beco sem saída irreversível entre status e orçamento.**
  `SOLICITACAO_RECEBIDA → ORCAMENTO_GERADO` era uma transição manual válida,
  mas `BudgetService.create` exige `SOLICITACAO_RECEBIDA` — quem avançasse o
  status pela tela deixava a WorkOrder afirmando ter orçamento **sem ter e
  sem nunca mais poder criar um** (criar retornava 409, PDF retornava 404,
  permanentemente). Reproduzido contra o sistema real antes da correção
- **Etapas podiam ser executadas sem orçamento aprovado** — era possível
  concluir a etapa de instalação com a WorkOrder ainda em
  `SOLICITACAO_RECEBIDA`
- **Etapas podiam ser executadas fora de ordem** — "Instalação" concluída
  com "Produção" e "Acabamento" ainda pendentes
- **Status caminhava até `FINALIZADO` sem nenhuma etapa executada** — o
  sistema declarava a entrega de um trabalho que nunca começou

### Alterado
- `WorkOrderStatusTransitions` passa a distinguir `isValid` (máquina
  completa) de `isManual` (o que o usuário dispara). A fase comercial
  inteira (`ORCAMENTO_GERADO`/`AGUARDANDO_APROVACAO`/`APROVADO`/`RECUSADO`)
  sai do conjunto manual e só é aplicada pelo sistema via
  `WorkOrderService.applyDerivedStatus`, como consequência de criar ou
  decidir um orçamento
- Iniciar a primeira etapa move `APROVADO → EM_EXECUCAO` sozinho — começar
  a trabalhar é o fato que define "em execução"
- Erros de transição agora dizem qual ação provoca o status pretendido
  ("O status muda sozinho ao criar o orçamento desta Ordem de Serviço") em
  vez de um "transição inválida" seco
- Frontend: botões da fase comercial deixam de existir e dão lugar à dica da
  ação que move o status; botão de iniciar etapa fica desabilitado com
  tooltip quando a etapa anterior não terminou; painel de etapas explica por
  que está somente-leitura, com texto diferente para "ainda não aprovado",
  "recusado" e "já concluída"

### Testado
- `WorkOrderStatusTransitionsTest` (6 cenários), incluindo uma verificação
  de que **nenhum** estado alcança a fase comercial manualmente — impede o
  beco sem saída de voltar por outro caminho
- `WorkOrderStepServiceTest` (7 cenários) e `WorkOrderServiceStatusGuardTest`
  (6 cenários): cada trava, mais os casos que devem continuar passando
  (WorkOrder sem etapas ainda pode ser entregue; bloquear etapa futura não
  exige a anterior concluída)
- 42 cenários no total. Fluxo feliz completo revalidado ponta a ponta contra
  o sistema real (criação → orçamento → aprovação → 3 etapas em ordem →
  entrega → finalização), e cada trava verificada pela API e pela tela

## [V2.3] — Timeline de Acompanhamento

### Adicionado
- `GET /work-orders/{uuid}/timeline` (`TimelineController`,
  `TimelineService`): histórico completo da WorkOrder em ordem cronológica,
  lendo os `domain_events` que já vinham sendo gravados desde a Sprint 3 —
  nenhum evento novo precisou ser criado, só consumido
- `TimelineDescriptionFormatter`: traduz `(event_type, payload)` em frase
  legível no backend, com os mesmos rótulos acentuados que os badges da tela
  usam. O frontend não interpreta `payload` — evita que cada consumidor
  futuro (Notificações, Automações) reimplemente a leitura e divirjam
- Frontend: botão "Ver histórico" no card da WorkOrder, com timeline
  vertical (linha + marcadores) mostrando descrição, autor e data/hora

### Corrigido
- **Timeline exibia eventos da mesma transação fora de ordem** — a aprovação
  de orçamento aparecia antes das transições de status que a causaram. Causa
  raiz: `domain_events.occurred_at` usa `DEFAULT now()`, e no PostgreSQL
  `now()` retorna o horário de início da **transação**, não do `INSERT`;
  eventos gravados juntos (ex: `BudgetService.updateStatus` grava duas
  transições + `ORCAMENTO_APROVADO`) ficam com timestamp idêntico e a
  ordenação só por `occurred_at` era indefinida. Corrigido ordenando por
  `occurred_at, id` (`BIGSERIAL` preserva a ordem de inserção).
  Encontrado ao validar a Timeline com dados reais, não em revisão de código

### Documentação
- **Contrato de `domain_events.payload` documentado por tipo de evento**
  (`docs/architecture.md`) — fecha o item de Dívida Técnica que pedia isso
  "antes de Notificações/Automações dependerem dele". Notificações Internas
  é justamente o próximo item do Kanban

### Testado
- `TimelineDescriptionFormatterTest` (8 cenários): é a validação executável
  do contrato de payload — mudar o formato num Service sem atualizar o
  formatter quebra o teste, em vez de falhar silenciosamente na tela.
  Cobre também tipo desconhecido e payload malformado (fallback, sem exceção)
- `TimelineServiceTest` (4 cenários): descrição/autor/timestamp, evento sem
  autor, isolamento multi-tenant e preservação da ordem vinda do repositório
- 23 cenários no total; `mvn clean test` limpo via Docker (Java 21)

## [V2.2] — PDF e Aprovação de Orçamento

### Adicionado
- Geração de PDF do orçamento (`BudgetPdfService`, biblioteca OpenPDF):
  `GET /work-orders/{uuid}/budget/pdf` retorna o documento com cabeçalho da
  empresa, cliente, WorkOrder, status, itens e total — restrito a
  `ADMIN_EMPRESA`/`OPERADOR`, mesmo critério das demais escritas do módulo
- `budgets` ganhou `decided_by_id`/`decided_at`, preenchidos em
  `BudgetService.updateStatus`: fecha o critério de aceitação "registrar
  aprovação/recusa com data/hora e responsável" que a V2.1 só cobria
  parcialmente (o evento ficava em `domain_events`, mas não no próprio
  orçamento) — agora aparece direto em `BudgetResponse` e no PDF
- Frontend: botão "Baixar PDF" no painel de orçamento (`WorkOrders.jsx`),
  download via blob; linha "Aprovado/Recusado por X em DD/MM/AAAA" exibida
  quando o orçamento já foi decidido

### Testado
- `BudgetServiceTest` ganhou 2 cenários novos (`generatePdf` delegando para
  `BudgetPdfService` com os dados corretos; isolamento multi-tenant do
  endpoint de PDF) e a asserção de `decidedBy`/`decidedAt` no teste de
  aprovação já existente — 11 cenários no total
- PDF real gerado e baixado de ponta a ponta (backend via Docker/Java 21,
  requisição autenticada, arquivo validado com assinatura `%PDF-1.5` e
  conteúdo conferido visualmente) e pela interface (clique em "Baixar PDF",
  sem erro de console)

## [V2.1] — Orçamentos e Catálogo

Primeira entrega da V2 (Núcleo Comercial e Fundação Técnica), seguindo o
Kanban ativo definido em `📌 Kanban Ativo — Sprint V2` (Notion).

### Adicionado
- Módulo de Catálogo (`CatalogItemController`, `CatalogItemService`):
  CRUD de produtos/serviços reutilizáveis por empresa (`catalog_items`),
  com nome, descrição, valor unitário e unidade. Segue o mesmo padrão de
  desativação simples (`is_active`) já usado no restante do projeto
- Módulo de Orçamentos (`BudgetController`, `BudgetService`): resolve a
  inconsistência identificada na auditoria cruzada de 12/jul — o enum
  `WorkOrderStatus` já previa `ORCAMENTO_GERADO`/`AGUARDANDO_APROVACAO`/
  `APROVADO`/`RECUSADO`, mas não existia entidade de orçamento para
  sustentar esse fluxo. `POST /work-orders/{uuid}/budget` cria o orçamento
  (um por WorkOrder — ver ADR-0002) e avança a WorkOrder para
  `ORCAMENTO_GERADO`; itens são adicionados/removidos via
  `/budget/items` com subtotal calculado automaticamente e total
  recalculado a cada mudança; `PATCH /budget/status` registra
  aprovação/recusa internamente pelo Operador (aprovação pública pelo
  Cliente é escopo do Portal, V3) e encadeia as transições
  `ORCAMENTO_GERADO → AGUARDANDO_APROVACAO → APROVADO|RECUSADO` reaproveitando
  `WorkOrderStatusTransitions` e `WorkOrderService.updateStatus` já
  existentes, em vez de duplicar a validação de estado
- Itens de orçamento gravam `description`/`unitPrice` como snapshot no
  momento da adição — alterações futuras no catálogo não afetam orçamentos
  já criados (podem referenciar um item de catálogo ou ser avulsos)
- Frontend: página `Catalog.jsx` (CRUD de catálogo, mesmo padrão de
  `Clients.jsx`) e painel de orçamento expansível no card de WorkOrder em
  `WorkOrders.jsx` (mesmo padrão do painel de Etapas da Sprint 4) —
  criar orçamento, adicionar item (do catálogo ou avulso), remover item,
  registrar aprovação/recusa
- `backend/src/test` criado nesta entrega — não existia até agora (Sprints
  1-4 validaram state machines com `javac` avulso, sem deixar teste
  versionado). `BudgetServiceTest` (9 cenários, JUnit 5 + Mockito) cobre as
  regras próprias do módulo: 1 orçamento por WorkOrder, edição só em
  `RASCUNHO`, snapshot de preço do catálogo, recálculo de total e a
  exigência de ao menos 1 item para aprovar/recusar. `mvn clean package`
  (via Docker, Java 21) roda limpo com os testes habilitados

### Débito técnico registrado (Épico Dívida Técnica)
- Flyway, CI (GitHub Actions) e Swagger/OpenAPI continuam pendentes —
  divergência encontrada entre a documentação (que os listava como
  "já entregues na Sprint 5") e o código real, que nunca teve Sprint 5.
  Decisão registrada em conversa com o responsável pelo projeto: esta
  entrega segue com `ddl-auto: validate` + `flowops_ddl.sql` manual
  (mesmo padrão das Sprints 1-4); Flyway/CI ficam como pendência explícita
  para uma entrega futura, não mais como "concluído" incorretamente
- O repositório real (`FlowOps`, branch `main`) estava parado no commit da
  Sprint 3 — o código da Sprint 4 (Etapas + Dashboard estendido) existia
  pronto só num pacote `.zip` de entrega, nunca commitado. Sincronizado e
  commitado nesta mesma entrega, antes do trabalho da V2.1, para que o
  histórico do repositório reflita o que está de fato implementado

### Decisão de arquitetura
- ADR-0002: orçamento sem versionamento (um por WorkOrder) — a state
  machine atual não sustenta reabertura de orçamento após `RECUSADO`
  (estado terminal), então versionamento seria complexidade sem caso de
  uso real hoje. Editar itens em `RASCUNHO` cobre o cenário real de ajuste
  antes da decisão

## [Sprint 4] — Etapas e Dashboard

### Adicionado
- CRUD de leitura/atualização de Etapas (`WorkOrderStepController`,
  `WorkOrderStepService`, `StepStatusTransitions`): `GET /work-orders/{uuid}/steps`
  lista as etapas de uma WorkOrder; `PATCH /work-orders/{uuid}/steps/{stepUuid}/status`
  avança o status e/ou registra observação (CU-019, CU-020, CU-022 em Negócio
  e Domínio)
- `StepStatusTransitions`, state machine isolada da etapa (mesmo padrão de
  `WorkOrderStatusTransitions` na Sprint 3): `PENDENTE → EM_ANDAMENTO →
  CONCLUIDA`, com `BLOQUEADA` acessível a partir de `PENDENTE`/`EM_ANDAMENTO`
  e reversível de volta. Diferença deliberada em relação à state machine da
  WorkOrder: aqui a self-transition (`isValid(x, x)`) é permitida para todo
  status não-terminal, porque o mesmo endpoint que avança o status também
  registra a observação (CU-022) — sem a self-transition, seria impossível
  salvar uma nota sem mudar o status. `CONCLUIDA` continua terminal mesmo
  para si mesma. Testada isoladamente com `javac` puro: 21 cenários (caminho
  feliz, bloqueio/desbloqueio, self-transition, terminal, pulos, retrocessos),
  todos passando
- Instanciação automática de Etapas na criação da WorkOrder: se a empresa tem
  um `workflow_template` marcado como `is_default`, `WorkOrderService.create`
  copia as `workflow_steps` do molde para `work_order_steps` da nova
  WorkOrder (Fluxo 3 — Planejamento, Negócio e Domínio). Sem template
  default, a WorkOrder nasce sem etapas — não é um erro
- Dashboard estendido (`DashboardService`, novo — a lógica que vivia direto
  no Controller desde a Sprint 1 agora segue o padrão Controller → Service →
  Repository do resto do projeto): além dos contadores por status, o resumo
  operacional agora traz as 5 WorkOrders mais recentes e as 5 próximas
  entregas agendadas (`scheduledEnd >= hoje`, excluindo WorkOrders em estado
  terminal)
- Frontend: seção de Etapas expansível em cada card de WorkOrder (`WorkOrders.jsx`)
  com badges de status, botões de avanço coerentes com `StepStatusTransitions`
  e campo de observação; Dashboard (`Dashboard.jsx`) ganhou as listas de
  WorkOrders recentes e próximas entregas

### Corrigido
- **`GET /auth/me` (e qualquer rota protegida) sem token retornava 403 em vez
  do 401 documentado em `docs/api.md`.** Bug pré-existente desde a Sprint 1,
  encontrado ao testar os requisitos das sprints anteriores durante esta
  entrega. Causa raiz: `SecurityConfig` nunca registrava um
  `AuthenticationEntryPoint` customizado; como login via formulário e HTTP
  Basic estão desabilitados (autenticação é só JWT stateless), o único
  entry point que o Spring Security registra por padrão nesse cenário é o
  `Http403ForbiddenEntryPoint`. O comentário em `JwtAuthenticationFilter` já
  assumia (incorretamente) que "o SecurityConfig vai bloquear com 401".
  Corrigido com `RestAuthenticationEntryPoint`, registrado explicitamente via
  `.exceptionHandling(ex -> ex.authenticationEntryPoint(...))`, devolvendo o
  mesmo formato de erro do `GlobalExceptionHandler`

### Alterado
- `work_order_steps` ganhou coluna `uuid` (schema original da Etapa 2.2 não
  previa isso). Adicionado para manter a mesma regra já aplicada a
  `clients` e `work_orders`: id sequencial interno nunca é exposto na API
  (ver `docs/architecture.md`) — sem isso, `WorkOrderStepResponse` teria que
  escolher entre quebrar essa regra ou usar `step_order` como identificador
  de rota, o que criaria uma exceção só para este módulo

## [Sprint 3] — WorkOrders

### Adicionado
- CRUD de WorkOrders com state machine completa (`WorkOrderController`,
  `WorkOrderService`, `WorkOrderStatusTransitions`)
- Transições de status validadas contra a state machine documentada desde
  a Sprint 1 (`SOLICITACAO_RECEBIDA → ... → FINALIZADO`), com estados
  terminais (`RECUSADO`, `FINALIZADO`) que não permitem nenhuma transição
  adicional
- Atribuição de responsável, com isolamento por empresa (não é possível
  atribuir um usuário de outra empresa)
- Registro de eventos de domínio (`domain_events`) em toda criação,
  transição de status e atribuição — primeira implementação real da
  Timeline documentada em Negócio e Domínio
- RBAC efetivamente aplicado via `@PreAuthorize` (`@EnableMethodSecurity`
  habilitado no `SecurityConfig`): criação, transição de status e
  atribuição restritas a `ADMIN_EMPRESA` e `OPERADOR`, conforme a matriz de
  permissões documentada desde a Sprint 1 mas nunca antes aplicada em código
- Endpoint mínimo `GET /users` (uuid + nome de usuários ativos), usado para
  popular o campo de responsável no frontend
- Frontend: tela de WorkOrders com filtro por status, paginação, criação e
  botões de avanço de status coerentes com as transições válidas

### Testado
- `WorkOrderStatusTransitions` compilada e testada isoladamente com `javac`
  puro (sem dependências de Spring) — 28 cenários cobrindo caminho feliz,
  estados terminais, pulos de etapa e tentativas de retrocesso, todos
  passando
- Ciclo de vida completo de uma WorkOrder (criação → transição →
  atribuição → timeline) validado contra PostgreSQL 16 real
- Isolamento multi-tenant de WorkOrders confirmado: uma WorkOrder de uma
  empresa é invisível a uma consulta feita com o `company_id` de outra

---

## [Sprint 2 — correções pós-review] — 6 jul/2026

### Corrigido
- **Login quebrava com 500 quando dois usuários ativos compartilhavam o
  mesmo e-mail em empresas diferentes.** Causa raiz: a constraint original
  era `UNIQUE(company_id, email)`, permitindo esse cenário, mas
  `UserRepository.findByEmailAndActiveTrue` espera um único resultado — com
  duas linhas, o Spring Data lança `IncorrectResultSizeDataAccessException`.
  Corrigido tornando o e-mail globalmente único (`UNIQUE(email)`). Ver ADR-0001.

### Adicionado
- Paginação, pesquisa por nome e navegação entre páginas no frontend de
  Clientes (o backend já suportava `Pageable` desde a criação do módulo)
- Exclusão inteligente de clientes: remoção física quando não há WorkOrders
  associadas, desativação (soft delete) quando há
- Validação de documento (CPF/CNPJ) único por empresa, com índice parcial no
  banco e checagem amigável no `ClientService` antes de qualquer erro de
  constraint chegar ao usuário
- Handler de `DataIntegrityViolationException` no `GlobalExceptionHandler`
  como rede de segurança (409), para qualquer violação de constraint que a
  validação da camada de serviço não tenha previsto

### Documentação
- `docs/adr/0001-modelo-de-usuario.md`: decisão de manter o modelo atual
  (usuário pertence a exatamente uma empresa) em vez de migrar para uma
  identidade de usuário global agora — com a alternativa considerada e os
  critérios para revisitar essa decisão no futuro

---

## [Sprint 2] — Gestão de Clientes

### Adicionado
- CRUD completo do módulo Clientes (`ClientController`, `ClientService`,
  `ClientRepository`)
- Tela de Clientes no frontend: listagem, criação, edição e desativação
- Isolamento multi-tenant reforçado em toda consulta por identificador
  (nunca `findById()` puro — sempre também filtrado por `company_id`)

### Decisão de arquitetura
- Identificadores expostos na API são sempre `uuid`, nunca o `id`
  sequencial interno (ver `docs/architecture.md`)

---

## [Sprint 1] — Fundação

### Adicionado
- Setup do projeto: Spring Boot 3 + React + Docker Compose
- Schema PostgreSQL criado a partir do DDL da Etapa 2.2, com dados de
  demonstração
- Autenticação JWT completa: login, rota protegida `/auth/me`, dashboard
  operacional isolado por empresa

### Corrigido
- **`LazyInitializationException` mascarada como HTTP 500** em
  `/api/v1/auth/me` e `/api/v1/dashboard/summary`.
  Causa raiz: `User.company` é `@ManyToOne(fetch = LAZY)`. O
  `JwtAuthenticationFilter` carrega o `User` fora de qualquer transação
  (filtros rodam antes do Controller/Service); como
  `spring.jpa.open-in-view` é propositalmente `false`, a sessão Hibernate
  que carregou o `User` já estava fechada quando o Controller acessava
  `user.getCompany()`. O login funcionava normalmente porque
  `AuthService.login()` roda dentro de uma transação — só os endpoints que
  liam o usuário a partir do filtro quebravam.
  Corrigido com `@EntityGraph(attributePaths = "company")` no
  `UserRepository`, forçando o carregamento via `JOIN` na mesma query,
  independente de contexto transacional.
- **Exceções genéricas engolidas em silêncio.** O `GlobalExceptionHandler`
  devolvia sempre "Erro interno inesperado" sem logar a exceção original,
  então o `LazyInitializationException` acima nunca aparecia nos logs.
  Corrigido com `log.error(...)` no handler genérico.
- **Build quebrada por incompatibilidade de versão do Java.** Uma alteração
  posterior do `Dockerfile`/`pom.xml` para Java 25 fazia o
  `spring-boot-maven-plugin` falhar no repackage
  (`Unsupported class file major version 69`), já que o Spring Boot 3.3.4
  não reconhece bytecode do JDK 25. Revertido para Java 21 (LTS), a versão
  validada contra esta versão do Spring Boot.
