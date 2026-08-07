# Guia de instalação e configuração

## Pré-requisitos

Você só precisa de **uma** destas duas opções:

### Opção A — Docker (recomendado)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado
  (inclui o Docker Compose)

### Opção B — Ambiente local (sem Docker)
- Java 21 ([Adoptium Temurin](https://adoptium.net/))
- Maven 3.9+
- Node.js 20+ e npm
- PostgreSQL 16 instalado localmente

Este guia foca na Opção A. A Opção B está detalhada na seção
[Rodando sem Docker](#rodando-sem-docker-desenvolvimento-local).

---

## Passo a passo — Docker Compose

### 1. Extraia o projeto e entre na pasta

```bash
cd flowops
```

### 2. Crie o arquivo de variáveis de ambiente

```bash
cp .env.example .env
```

Para rodar localmente pela primeira vez, os valores padrão já funcionam sem
alterar nada. Para um ambiente que não seja apenas local, troque o
`JWT_SECRET` por uma chave própria:

```bash
openssl rand -hex 32
```

### 3. Suba os serviços

```bash
docker compose up --build
```

Isso vai:
1. Criar o container do PostgreSQL 16 **vazio**
2. Subir o MinIO (storage de evidências, V2.6) nas portas `9000` (API S3) e
   `9001` (console web). O bucket é criado sozinho na subida do backend
3. Buildar a imagem do backend (Maven + Spring Boot) e subir na porta `8080`.
   Na subida, o **Flyway** cria as 15 tabelas e — por estar no profile `dev` —
   também aplica os dados de demonstração
4. Buildar a imagem do frontend (Vite dev server) e subir na porta `5173`

> O schema é criado pelo Flyway, não por script montado no Postgres. É o
> mesmo caminho usado em produção: num banco gerenciado (Supabase) ninguém
> roda o script de inicialização por você, e um schema que só funciona
> localmente esconderia esse problema até o dia do deploy.

A primeira execução demora alguns minutos (download de dependências). As
próximas são bem mais rápidas graças ao cache das camadas do Docker.

Nenhuma conta em serviço de nuvem é necessária: o MinIO faz o papel do S3
localmente. Para inspecionar os arquivos enviados, abra
`http://localhost:9001` e entre com `STORAGE_ACCESS_KEY`/`STORAGE_SECRET_KEY`
(padrão `flowops` / `flowops123`).

> **Indo para produção:** troque `STORAGE_ENDPOINT` e
> `STORAGE_PUBLIC_ENDPOINT` para o provedor S3-compatível escolhido (AWS S3,
> Cloudflare R2, Backblaze B2) e ajuste as credenciais. Nenhuma mudança de
> código é necessária. Atenção: `STORAGE_PUBLIC_ENDPOINT` precisa ser o host
> que o **navegador** alcança — as URLs de upload são assinadas para ele.

### 4. Confirme que os serviços estão de pé

```bash
docker compose ps
```

Espere ver `db`, `backend` e `frontend` com status `running` (ou `healthy`
no caso do `db` e do `backend`).

Se o `backend` continuar reiniciando, veja os logs:

```bash
docker compose logs backend --tail=100
```

### 5. Acesse o sistema

Abra **http://localhost:5173** no navegador.

O banco já sobe com uma empresa e usuários de demonstração (um para cada
perfil: administrador, operador e técnico). As credenciais estão em
`backend/src/main/resources/db/seed/V100__seed_demo_data.sql` — abra esse
arquivo para ver os e-mails cadastrados; a senha usada em todos eles está
comentada logo acima do `INSERT` de usuários.

> Esse seed **só existe no profile `dev`**. Em produção o banco sobe apenas
> com o schema, e o primeiro administrador é criado a partir das variáveis
> `FLOWOPS_BOOTSTRAP_*` (ver seção de variáveis de ambiente).

Depois do login, você cai no Dashboard e em seguida pode navegar até
Clientes para testar o CRUD completo do módulo.

### 6. Para encerrar

```bash
docker compose down
```

Isso mantém o volume do banco intacto. Para apagar tudo e recomeçar do zero
(recriar schema e dados de demonstração na próxima subida):

```bash
docker compose down -v
```

---

## Variáveis de ambiente

| Variável | Descrição | Padrão |
|---|---|---|
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Credenciais do PostgreSQL | `flowops` |
| `JWT_SECRET` | Chave de assinatura do token JWT | ver `.env.example` |
| `JWT_EXPIRATION_MINUTES` | Tempo de validade do token | `60` |
| `CORS_ALLOWED_ORIGINS` | Origem permitida para chamadas ao backend | `http://localhost:5173` |
| `VITE_API_URL` | URL base da API usada pelo frontend | `http://localhost:8080/api/v1` |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | Credenciais do storage | `flowops` / `flowops123` |
| `STORAGE_BUCKET` | Bucket das evidências (criado sozinho) | `flowops-evidences` |
| `STORAGE_ENDPOINT` | Endpoint que o **backend** usa | `http://storage:9000` |
| `STORAGE_PUBLIC_ENDPOINT` | Endpoint que o **navegador** usa — as URLs são assinadas para ele | `http://localhost:9000` |
| `STORAGE_MAX_FILE_SIZE_MB` | Tamanho máximo por evidência | `15` |
| `STORAGE_REGION` | Região usada para assinar as URLs | `us-east-1` |
| `SPRING_PROFILES_ACTIVE` | `dev` (com seed) ou `prod` (sem seed) | `dev` |

### Só em produção

Em `prod` os segredos **não têm valor padrão**: se a variável faltar, a
aplicação recusa subir em vez de usar silenciosamente a chave de exemplo que
está pública neste repositório. São obrigatórias: `JWT_SECRET`,
`CORS_ALLOWED_ORIGINS` e todas as `STORAGE_*`.

Como o seed não roda em `prod`, o banco sobe sem nenhum usuário — e ninguém
consegue entrar. Estas variáveis criam a primeira empresa e o primeiro
administrador, e **só agem se o sistema estiver vazio** (reiniciar não
duplica nada):

| Variável | Descrição |
|---|---|
| `FLOWOPS_BOOTSTRAP_ADMIN_EMAIL` | E-mail do primeiro administrador |
| `FLOWOPS_BOOTSTRAP_ADMIN_PASSWORD` | Senha inicial (mínimo 8 caracteres) |
| `FLOWOPS_BOOTSTRAP_ADMIN_NAME` | Nome exibido (padrão: `Administrador`) |
| `FLOWOPS_BOOTSTRAP_COMPANY_NAME` | Nome da empresa (padrão: `Minha Empresa`) |

Depois do primeiro acesso elas podem ser removidas — a senha passa a ser
gerenciada pela tela de Perfil.

Os dois endpoints de storage são diferentes de propósito quando se roda via
Docker: o backend fala com o container pela rede interna, mas a URL
pré-assinada precisa apontar para um host que o navegador consiga acessar —
e a assinatura inclui esse host. Apontar os dois para o mesmo valor faz o
upload falhar em um dos dois lados.

---

## Rodando sem Docker (desenvolvimento local)

Use esta opção se quiser rodar o backend na sua IDE (IntelliJ, VS Code) com
debug e hot-reload.

### Banco de dados

```bash
createdb flowops
```

Só isso. Não rode SQL na mão: o Flyway cria o schema e aplica o seed na
primeira subida do backend, contanto que o profile seja `dev` (é o padrão).

### Backend

```bash
cd backend

export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=flowops
export DB_USER=flowops
export DB_PASSWORD=flowops
export JWT_SECRET=troque-esta-chave-em-producao-minimo-256-bits-0123456789abcdef

mvn spring-boot:run
```

A API sobe em `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

O Vite sobe em `http://localhost:5173` e já aponta para
`http://localhost:8080/api/v1` por padrão.

---

## Solução de problemas

**`backend` fica em loop de restart**
Veja os logs (`docker compose logs backend`). Na maioria das vezes é o
backend subindo antes do Postgres aceitar conexões — o `depends_on` com
`condition: service_healthy` já deveria evitar isso, mas se acontecer, rode
`docker compose up` novamente sem `--build`.

**Erro 401 em todas as rotas, mesmo depois do login**
Confira se o `JWT_SECRET` usado para gerar o token é o mesmo configurado no
backend no momento da validação. Isso normalmente acontece quando o `.env`
muda e o container não é recriado (`docker compose up --build backend`).

**Tela de login não carrega / erro de CORS no console do navegador**
Confirme que `CORS_ALLOWED_ORIGINS` no `.env` bate exatamente com a URL que
você está usando no navegador (incluindo `http://` e a porta).

**`docker compose logs` retorna "no configuration file provided: not found"**
O comando foi rodado fora da pasta do projeto. O Docker Compose procura um
`docker-compose.yml` no diretório atual — entre na pasta `flowops` antes de
rodar qualquer comando.

**Quero recriar o hash de senha de um usuário de demonstração**

```python
import bcrypt
print(bcrypt.hashpw(b"SuaNovaSenha", bcrypt.gensalt(rounds=10)).decode())
```

Substitua o valor de `password_hash` no `seed.sql` (ou direto no banco) pelo
hash gerado.

Para detalhes de correções já aplicadas ao projeto, veja o
[`CHANGELOG.md`](../CHANGELOG.md). Para o raciocínio por trás das decisões
de arquitetura, veja o [`docs/architecture.md`](architecture.md).
