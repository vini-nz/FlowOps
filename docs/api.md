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

## Perfil

Dados do **próprio usuário** (V2.8). Sem restrição por papel; o usuário vem
do token, nunca de parâmetro de rota.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/profile` | Dados do usuário autenticado |
| `PUT` | `/profile` | Atualiza nome e e-mail |
| `PATCH` | `/profile/password` | Troca a senha |

**Request de perfil:**
```json
{ "name": "Operador", "email": "operador@empresa.com", "currentPassword": "..." }
```
`currentPassword` só é exigida **quando o e-mail muda** — o e-mail é a
credencial de login, então a troca não pode depender apenas de um token
válido. E-mail já em uso retorna `409`; senha ausente ou errada, `401`.

**Request de senha:**
```json
{ "currentPassword": "...", "newPassword": "mínimo 8 caracteres" }
```
Senha atual errada retorna `401`; nova senha igual à atual, `409`.

### O campo `accessToken` da resposta

Quando a alteração invalida o token em uso, a resposta traz um
`accessToken` novo e **o cliente precisa adotá-lo** — caso contrário a
requisição seguinte recebe `401`. Isso acontece em dois casos:

- **Troca de senha:** marca `password_changed_at`, e o filtro passa a recusar
  todo JWT emitido antes disso. É o que faz a troca de senha realmente
  derrubar as outras sessões, em vez de só mudar o hash.
- **Troca de e-mail:** o `sub` do JWT é o e-mail, então o token antigo deixa
  de resolver o usuário.

Nos demais casos (só o nome mudou), `accessToken` vem `null` e o token atual
continua válido.

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
{ "status": "EM_EXECUCAO" }
```

Este endpoint aceita **apenas as transições manuais** (V2.4 / ADR-0003):
`APROVADO → EM_EXECUCAO`, `EM_EXECUCAO → ENTREGUE` e
`ENTREGUE → FINALIZADO`.

A fase comercial não é manual — é consequência de ações no orçamento:

| Status pretendido | Como acontece |
|---|---|
| `ORCAMENTO_GERADO` | `POST /work-orders/{uuid}/budget` |
| `AGUARDANDO_APROVACAO`, `APROVADO`, `RECUSADO` | `PATCH /work-orders/{uuid}/budget/status` |
| `EM_EXECUCAO` | manual, ou automático ao iniciar a primeira etapa |

Tentar essas transições aqui retorna `409` com a ação que de fato as
provoca (ex: *"O status muda sozinho ao criar o orçamento desta Ordem de
Serviço"*). `EM_EXECUCAO → ENTREGUE` retorna `409` se alguma etapa não
estiver `CONCLUIDA` — uma WorkOrder sem etapas pode ser entregue
normalmente. Uma transição fora da máquina de estados (ex:
`SOLICITACAO_RECEBIDA` direto para `FINALIZADO`) também retorna `409`.

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

Além da própria state machine, desde a V2.4 (ADR-0003) valem duas travas de
integridade, ambas com `409`:

- A WorkOrder precisa estar em `APROVADO` ou `EM_EXECUCAO`. Não se trabalha
  uma etapa antes do orçamento aprovado, nem depois da entrega.
- Uma etapa só **inicia** (`EM_ANDAMENTO`) com todas as anteriores por
  `stepOrder` em `CONCLUIDA` — a mensagem diz qual etapa falta. Marcar
  `BLOQUEADA` numa etapa futura continua permitido: sinalizar impedimento
  não é começar o trabalho.

Iniciar a primeira etapa move a WorkOrder de `APROVADO` para `EM_EXECUCAO`
automaticamente.

### Evidências da etapa

Arquivos (fotos/PDF) anexados a uma etapa. O binário **não passa pela API**:
vai do navegador direto ao storage por URL pré-assinada (ver ADR-0004).

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `.../steps/{stepUuid}/evidences` | Lista as evidências confirmadas |
| `POST` | `.../steps/{stepUuid}/evidences/upload-url` | Valida e devolve a URL pré-assinada de envio |
| `POST` | `.../steps/{stepUuid}/evidences/{uuid}/confirm` | Confirma que o arquivo chegou ao storage |
| `GET` | `.../steps/{stepUuid}/evidences/{uuid}/download-url` | URL assinada de leitura, curta duração |
| `DELETE` | `.../steps/{stepUuid}/evidences/{uuid}` | Remove metadado e objeto |

Escrita restrita a `ADMIN_EMPRESA`, `OPERADOR` e `TECNICO` (mesma linha
"Anexar Evidências" da matriz RBAC).

**Fluxo de envio, em três passos:**

```
1. POST .../evidences/upload-url
   { "fileName": "foto.jpg", "contentType": "image/jpeg", "sizeBytes": 204800 }
   → { "evidenceUuid": "...", "uploadUrl": "http://...", "expiresInMinutes": 10 }

2. PUT <uploadUrl>            (direto no storage, com header Content-Type)
   ⚠ sem header Authorization: ele não faz parte da assinatura e o storage recusa

3. POST .../evidences/{evidenceUuid}/confirm
   → a evidência passa a aparecer na galeria
```

Enquanto o passo 3 não acontece, a evidência **não existe** para o sistema:
não aparece na listagem nem pode ser baixada. Confirmar um upload que nunca
chegou retorna `409`.

Tipos aceitos: `image/jpeg`, `image/png`, `image/webp`, `image/heic` e
`application/pdf`. Limite padrão de 15 MB (`STORAGE_MAX_FILE_SIZE_MB`).
Tipo ou tamanho fora do permitido retorna `409` **antes** de qualquer URL ser
emitida. Valem também as travas da V2.4: `409` se a WorkOrder não estiver em
`APROVADO`/`EM_EXECUCAO` ou se a etapa já estiver `CONCLUIDA`.

### Checklist da etapa

A resposta da etapa traz `checklistItems`, com `fromTemplate` indicando se o
item veio do molde da empresa ou foi criado avulso nesta OS.

| Método | Rota | Descrição |
|---|---|---|
| `PATCH` | `.../steps/{stepUuid}/checklist/{itemUuid}` | Marca/desmarca (`{ "done": true }`), registrando quem e quando |
| `POST` | `.../steps/{stepUuid}/checklist` | Acrescenta item avulso a esta OS |
| `DELETE` | `.../steps/{stepUuid}/checklist/{itemUuid}` | Remove item — **só avulsos** |

Mesmas permissões de "Atualizar Etapas" (`ADMIN_EMPRESA`, `OPERADOR`,
`TECNICO`) e mesmas travas: `409` se a WorkOrder não estiver em
`APROVADO`/`EM_EXECUCAO`, ou se a etapa já estiver `CONCLUIDA`. Remover um
item vindo do molde retorna `409` — esconderia numa OS uma exigência que a
empresa definiu; para isso, altere o workflow.

## Workflow (moldes de etapa)

Define quais etapas — e quais itens de checklist — uma WorkOrder nova recebe.
Escrita restrita a `ADMIN_EMPRESA`; leitura aberta aos demais papéis.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/workflows` | Lista os workflows da empresa, com etapas e checklist |
| `GET` | `/workflows/{uuid}` | Detalhe de um workflow |
| `POST` | `/workflows` | Cria um workflow. O primeiro da empresa vira padrão automaticamente |
| `PUT` | `/workflows/{uuid}` | Renomeia e/ou define como padrão |
| `DELETE` | `/workflows/{uuid}` | Exclui o workflow e suas etapas |
| `POST` | `/workflows/{uuid}/steps` | Acrescenta uma etapa ao fim |
| `PUT` | `/workflows/{uuid}/steps/{stepUuid}` | Renomeia a etapa |
| `DELETE` | `/workflows/{uuid}/steps/{stepUuid}` | Remove a etapa e reordena as demais |
| `PATCH` | `/workflows/{uuid}/steps/{stepUuid}/move?direction=up\|down` | Move a etapa uma posição |
| `POST` | `/workflows/{uuid}/steps/{stepUuid}/checklist` | Acrescenta item de checklist ao molde |
| `DELETE` | `/workflows/{uuid}/steps/{stepUuid}/checklist/{itemUuid}` | Remove item do molde |

Toda escrita devolve o workflow inteiro atualizado, para a tela não precisar
de um `GET` extra.

**Request de workflow:**
```json
{ "name": "Assistência Técnica", "isDefault": true }
```
`isDefault: false` num workflow que já é o padrão retorna `409` — defina
outro como padrão em vez de deixar a empresa sem nenhum. Excluir o padrão
quando existem outros workflows também retorna `409`, pelo mesmo motivo:
sem padrão, as WorkOrders voltariam a nascer sem etapas.

**Request de etapa:** `{ "title": "Diagnóstico" }`
**Request de item de checklist:** `{ "description": "Testar alimentação" }`

**Editar um workflow nunca altera Ordens de Serviço existentes.** Etapas e
checklist são copiados no momento da criação da WorkOrder — ver
`docs/architecture.md`.

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
| `GET` | `/work-orders/{workOrderUuid}/budget/pdf` | Baixa o PDF do orçamento (`application/pdf`, `Content-Disposition: attachment`) — **restrito a `ADMIN_EMPRESA` e `OPERADOR`** |

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
orçamento já decidido, ou sem itens, retorna `409`. Preenche `decidedByName`
e `decidedAt` na resposta do orçamento (ambos `null` enquanto `RASCUNHO`).

## Timeline

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/work-orders/{workOrderUuid}/timeline` | Histórico completo da WorkOrder em ordem cronológica |

Leitura liberada a qualquer papel autenticado da empresa (mesma regra do
`GET` de WorkOrders e Etapas) — o isolamento multi-tenant vem do filtro por
`company_id`.

**Response:**
```json
[
  {
    "eventType": "STATUS_ALTERADO",
    "description": "Status alterado de Solicitação recebida para Orçamento gerado",
    "actorName": "Operador Demonstração",
    "occurredAt": "2026-07-24T22:49:33.763745Z"
  }
]
```

`description` já vem pronta para exibição — o cliente da API nunca precisa
interpretar `domain_events.payload` (contrato por tipo de evento em
`docs/architecture.md`). `actorName` é `null` em eventos sem autor.
`eventType` é exposto para permitir ícone/estilo por tipo no frontend, mas um
tipo desconhecido não quebra nada: `description` cai no próprio `eventType`.

## Notificações

Notificações in-app do **próprio usuário** (V2.7), geradas a partir de
`domain_events`. Sem restrição por papel: cada um lê e marca as suas. O
destinatário vem sempre do token — nunca de parâmetro da requisição.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/notifications` | Lista paginada, mais recentes primeiro |
| `GET` | `/notifications/unread-count` | `{ "count": 3 }` — usado pelo sino |
| `PATCH` | `/notifications/{uuid}/read` | Marca uma como lida |
| `PATCH` | `/notifications/read-all` | Marca todas, devolve `{ "updated": n }` |

Só `STATUS_ALTERADO` e `RESPONSAVEL_ATRIBUIDO` geram notificação — eventos de
rotina (checklist, evidência, itens de orçamento) ficam só na Timeline, para
o sino não virar ruído. O destinatário é o responsável pela WorkOrder, e
**nunca quem provocou o evento**.

Uma notificação pertence a uma pessoa: tentar marcar como lida a de outro
usuário, mesmo da mesma empresa, retorna `404`.

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
