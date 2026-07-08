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
| `GET` | `/dashboard/summary` | Contadores de WorkOrders por status, isolados por empresa |

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
