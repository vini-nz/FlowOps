# ADR-0004 — Storage de evidências: MinIO local e URL pré-assinada

**Status:** Aceito
**Data:** 27 de julho de 2026

## Contexto

O item 3 do Backlog Detalhado (Evidências) é a primeira funcionalidade do
FlowOps que precisa guardar arquivos binários. Até a V2.5 o projeto não tinha
nenhuma dependência de serviço externo: `docker compose up` sobe o sistema
inteiro e qualquer pessoa que clone o repositório consegue rodar.

Duas restrições reais moldam a decisão:

1. **Filesystem não é opção.** Em provedores como Railway e Render o disco do
   contêiner é efêmero — arquivos gravados em pasta local desaparecem no
   próximo deploy. Seria uma bomba-relógio, não uma simplificação.
2. **Exigir conta em nuvem tem custo de adoção.** Pedir credenciais AWS para
   rodar o projeto quebraria o "clone e rode", que é uma qualidade real para
   um projeto acadêmico/portfólio.

## Decisão

**Storage S3-compatível, com MinIO rodando no próprio `docker-compose`**, e
upload por **URL pré-assinada** (o navegador envia direto ao storage, sem o
arquivo passar pelo backend).

O código usa o SDK oficial da AWS (`software.amazon.awssdk:s3`). Migrar para
S3, Cloudflare R2 ou Backblaze B2 em produção é troca de variável de
ambiente — nenhuma linha de código muda.

## O detalhe que faz a URL pré-assinada funcionar

A assinatura SigV4 cobre o cabeçalho `Host`. Dentro do Docker o backend
alcança o MinIO em `http://storage:9000`, mas o navegador só enxerga
`http://localhost:9000` — assinar com o host interno produziria uma URL que o
browser nem resolve, e cuja assinatura não conferiria.

Por isso existem **dois clientes** (`StorageConfig`):

| Bean | Endpoint | Uso |
|---|---|---|
| `S3Client` | interno (`storage:9000`) | criar bucket, `headObject`, excluir |
| `S3Presigner` | público (`localhost:9000`) | gerar as URLs que o navegador chama |

`pathStyleAccessEnabled(true)` é obrigatório: o MinIO não usa o esquema
`bucket.host` da AWS.

## Consequência: o registro órfão

Como o backend não participa da transferência, ele não pode gravar o
metadado *depois* do upload — precisa registrar antes, para poder assinar a
URL. Então `evidences` nasce com `uploaded_at` nulo e só passa a existir para
o sistema depois de `POST /confirm`, que faz `headObject` no storage para
confirmar que o objeto chegou de fato.

Isso significa que **uploads abandonados deixam linhas pendentes**. Duas
salvaguardas:

- Toda listagem filtra `uploaded_at IS NOT NULL` — um órfão nunca aparece na
  galeria nem pode ser baixado.
- Existe índice parcial `idx_evidences_pending` para varrer esses registros.

A rotina de limpeza periódica **não foi implementada** e está registrada como
dívida técnica. Não é urgente: o custo de uma linha órfã é desprezível e o
objeto correspondente nem chegou a existir no storage.

O `confirm` também **ignora o tamanho informado pelo cliente** e regrava com
o valor real lido do storage — o backend não presenciou o upload, então o
único número confiável é o do próprio storage.

## Alternativas consideradas

- **Upload através do backend (multipart).** Mais simples e sem órfão: o
  backend valida e grava numa operação só. Descartada porque o backlog
  especificava pré-assinada e porque ela é a arquitetura correta em escala —
  o backend deixa de ser gargalo de banda. O custo é o tratamento de órfão,
  que é contornável.
- **Guardar em `bytea` no PostgreSQL.** Zero infraestrutura nova e backup
  junto dos dados, mas infla o banco, encarece backup e não escala para
  binários.
- **S3 real desde já.** Mais próximo de produção, mas exige conta e
  credenciais para rodar o projeto localmente.

## Quando revisitar

Se o volume de arquivos crescer a ponto de a limpeza de órfãos importar, ou
se o sistema for para produção de verdade: nesse momento troca-se o endpoint
para um provedor gerenciado (nada de código muda) e implementa-se a rotina de
limpeza. Se o upload direto do navegador virar um problema de política de
segurança (ex: exigir varredura antivírus antes de aceitar o arquivo), aí sim
o modelo pré-assinado deve dar lugar ao upload através do backend.
