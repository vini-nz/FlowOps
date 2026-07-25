# Changelog

Todas as mudanças relevantes do projeto são registradas aqui.
Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

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
