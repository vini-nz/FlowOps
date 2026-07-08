# Changelog

Todas as mudanças relevantes do projeto são registradas aqui.
Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

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
