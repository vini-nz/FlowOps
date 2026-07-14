# Referência da API

Base URL: `http://localhost:8080/api/v1`

Todas as rotas, exceto `/auth/login`, exigem o header:

```
Authorization: Bearer <token>
```

## Autenticação

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/auth/login` | Autentica com e-mail e senha, retorna o JWT |
| `GET` | `/auth/me` | Retorna os dados do usuário autenticado |

**Request de login:**
```json
{ "email": "usuario@empresa.com", "password": "senha" }
```

**Response:**
```json
{
  "accessToken": "...",
  "tokenType": "Bearer",
  "user": {
    "uuid": "...",
    "name": "...",
    "email": "...",
    "role": "ADMIN_EMPRESA",
    "companyId": 1,
    "companyName": "..."
  }
}
```

## Dashboard

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/dashboard/summary` | Contadores por status, WorkOrders recentes e próximas entregas, isolados por empresa |

**Response:**
```json
{
  "companyName": "...",
  "totalWorkOrders": 5,
  "byStatus": { "SOLICITACAO_RECEBIDA": 2, "EM_EXECUCAO": 1 },
  "recentWorkOrders": [
    { "uuid": "...", "title": "...", "clientName": "...", "status": "EM_EXECUCAO", "scheduledEnd": "2026-08-15" }
  ],
  "upcomingDeliveries": [
    { "uuid": "...", "title": "...", "clientName": "...", "status": "APROVADO", "scheduledEnd": "2026-08-01" }
  ]
}
```

`recentWorkOrders` traz as 5 WorkOrders mais recentes (por `created_at`).
`upcomingDeliveries` traz as 5 WorkOrders com `scheduledEnd` a partir de hoje
que ainda não chegaram a um estado terminal (`FINALIZADO`/`RECUSADO`),
ordenadas pela data de entrega mais próxima.

## Clientes

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/clients` | Lista paginada de clientes ativos. Aceita `page`, `size`, `sort` (ex: `name,asc`) e `search` (filtro por nome) |
| `GET` | `/clients/{uuid}` | Detalhe de um cliente |
| `POST` | `/clients` | Cria um cliente |
| `PUT` | `/clients/{uuid}` | Atualiza um cliente |
| `DELETE` | `/clients/{uuid}` | Remove um cliente: exclusão física se não houver WorkOrders associadas, ou desativação (soft delete) caso contrário |

**Request de criação/atualização:**
```json
{
  "name": "Nome do cliente",
  "email": "cliente@exemplo.com",
  "phone": "(00) 00000-0000",
  "document": "000.000.000-00",
  "notes": "Observações opcionais"
}
```

Apenas `name` é obrigatório.

## WorkOrders

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/work-orders` | Lista paginada. Aceita `page`, `size`, `sort`, e `status` (filtro exato) |
| `GET` | `/work-orders/{uuid}` | Detalhe de uma WorkOrder |
| `POST` | `/work-orders` | Cria uma WorkOrder — **restrito a `ADMIN_EMPRESA` e `OPERADOR`** |
| `PATCH` | `/work-orders/{uuid}/status` | Avança o status, validado pela state machine — **restrito a `ADMIN_EMPRESA` e `OPERADOR`** |
| `PATCH` | `/work-orders/{uuid}/assign` | Atribui (ou remove) o responsável — **restrito a `ADMIN_EMPRESA` e `OPERADOR`** |

**Request de criação:**
```json
{
  "clientUuid": "uuid-do-cliente",
  "title": "Armário planejado",
  "description": "Opcional",
  "priority": "NORMAL",
  "scheduledStart": "2026-08-01",
  "scheduledEnd": "2026-08-15",
  "assignedToUuid": "uuid-do-usuario (opcional)"
}
```

Apenas `clientUuid` e `title` são obrigatórios. `priority` aceita
`BAIXA`, `NORMAL`, `ALTA`, `URGENTE` (padrão `NORMAL`).

**Request de transição de status:**
```json
{ "status": "ORCAMENTO_GERADO" }
```

Transições seguem a state machine documentada em `docs/architecture.md`.
Uma transição inválida (ex: pular de `SOLICITACAO_RECEBIDA` direto para
`FINALIZADO`) retorna `409` com a mensagem do que foi tentado.

**Request de atribuição:**
```json
{ "assignedToUuid": "uuid-do-usuario" }
```

Envie `assignedToUuid: null` para remover o responsável atual.

## Etapas

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/work-orders/{workOrderUuid}/steps` | Lista as etapas de uma WorkOrder, ordenadas por `stepOrder` |
| `PATCH` | `/work-orders/{workOrderUuid}/steps/{stepUuid}/status` | Avança o status da etapa e/ou registra observação — **restrito a `ADMIN_EMPRESA`, `OPERADOR` e `TECNICO`** |

Uma WorkOrder só tem etapas se a empresa tiver um `workflow_template`
marcado como padrão no momento em que ela foi criada — nesse caso a lista
vem vazia (`[]`), não é um erro.

**Request de atualização:**
```json
{ "status": "EM_ANDAMENTO", "notes": "Observação opcional" }
```

`status` é obrigatório; `notes` é opcional. Transições seguem
`StepStatusTransitions` (`docs/architecture.md`): `PENDENTE → EM_ANDAMENTO →
CONCLUIDA`, com `BLOQUEADA` acessível a partir de `PENDENTE`/`EM_ANDAMENTO` e
reversível de volta. `CONCLUIDA` é terminal. Diferente da state machine da
WorkOrder, enviar o mesmo `status` atual é uma transição válida — é assim que
se registra uma observação sem avançar a etapa. Uma transição realmente
inválida (ex: `PENDENTE` direto para `CONCLUIDA`, ou qualquer mudança a
partir de `CONCLUIDA`) retorna `409`.

## Catálogo

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/catalog-items` | Lista paginada de itens ativos. Aceita `page`, `size`, `sort` e `search` (filtro por nome) |
| `POST` | `/catalog-items` | Cria um item — **restrito a `ADMIN_EMPRESA` e `OPERADOR`** |
| `PUT` | `/catalog-items/{uuid}` | Atualiza um item — **restrito a `ADMIN_EMPRESA` e `OPERADOR`** |
| `DELETE` | `/catalog-items/{uuid}` | Desativa um item (`is_active = false`) — **restrito a `ADMIN_EMPRESA` e `OPERADOR`** |

**Request de criação/atualização:**
```json
{ "name": "Hora técnica", "description": "Opcional", "unitPrice": 150.00, "unit": "HORA" }
```

`name` e `unitPrice` são obrigatórios. `unit` é livre (`UN`, `HORA`, `M2`...),
padrão `UN`.

## Orçamentos

Sub-recurso de WorkOrders — um orçamento por WorkOrder (ver ADR-0002, sem
versionamento). Itens só podem ser adicionados/removidos e o status só pode
ser decidido enquanto o orçamento está em `RASCUNHO`.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/work-orders/{workOrderUuid}/budget` | Detalhe do orçamento com itens. `404` se ainda não existir orçamento para esta WorkOrder |
| `POST` | `/work-orders/{workOrderUuid}/budget` | Cria o orçamento — só válido com a WorkOrder em `SOLICITACAO_RECEBIDA`; avança para `ORCAMENTO_GERADO` — **restrito a `ADMIN_EMPRESA` e `OPERADOR`** |
| `POST` | `/work-orders/{workOrderUuid}/budget/items` | Adiciona um item, recalcula o total — **restrito a `ADMIN_EMPRESA` e `OPERADOR`** |
| `DELETE` | `/work-orders/{workOrderUuid}/budget/items/{itemUuid}` | Remove um item, recalcula o total — **restrito a `ADMIN_EMPRESA` e `OPERADOR`** |
| `PATCH` | `/work-orders/{workOrderUuid}/budget/status` | Registra aprovação/recusa (interno, pelo Operador) e avança a WorkOrder — **restrito a `ADMIN_EMPRESA` e `OPERADOR`** |

**Request de item (do catálogo):**
```json
{ "catalogItemUuid": "uuid-do-item", "quantity": 2 }
```
`description` e `unitPrice` são opcionais quando `catalogItemUuid` é
informado — herdam o snapshot do item de catálogo no momento da adição.

**Request de item (avulso):**
```json
{ "description": "Material extra", "quantity": 1, "unitPrice": 50.00 }
```
Sem `catalogItemUuid`, `description` e `unitPrice` passam a ser obrigatórios
(`400`/`409` se ausentes).

**Request de decisão:**
```json
{ "status": "APROVADO" }
```
Aceita `APROVADO` ou `RECUSADO` (`RASCUNHO` não é um valor válido de
destino). Exige ao menos um item e o orçamento ainda em `RASCUNHO` — um
orçamento já decidido, ou sem itens, retorna `409`.

## Usuários

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/users` | Lista usuários ativos da empresa (uuid + nome) — usado para popular o campo de responsável |

Não é um módulo de gestão de usuários completo — apenas o suficiente para
alimentar o dropdown de responsável em WorkOrders.

## Códigos de status

| Código | Quando acontece |
|---|---|
| `200` | Sucesso (GET, PUT) |
| `201` | Recurso criado (POST) |
| `204` | Recurso desativado, sem corpo de resposta (DELETE) |
| `400` | Erro de validação nos dados enviados |
| `401` | Token ausente, inválido ou expirado |
| `403` | Autenticado, mas o papel (role) não tem permissão para essa ação |
| `404` | Recurso não encontrado (ou pertence a outra empresa) |
| `409` | Conflito com dados existentes (ex: documento de cliente já cadastrado na empresa) |
| `500` | Erro inesperado — verifique `docker compose logs backend` |
