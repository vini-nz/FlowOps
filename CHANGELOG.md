# Changelog

Todas as mudanças relevantes do projeto são registradas aqui.
Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

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
