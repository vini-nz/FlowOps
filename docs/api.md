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

## Códigos de status

| Código | Quando acontece |
|---|---|
| `200` | Sucesso (GET, PUT) |
| `201` | Recurso criado (POST) |
| `204` | Recurso desativado, sem corpo de resposta (DELETE) |
| `400` | Erro de validação nos dados enviados |
| `401` | Token ausente, inválido ou expirado |
| `404` | Recurso não encontrado (ou pertence a outra empresa) |
| `409` | Conflito com dados existentes (ex: documento de cliente já cadastrado na empresa) |
| `500` | Erro inesperado — verifique `docker compose logs backend` |
