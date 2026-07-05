# FlowOps — Sprint 1 (Fundação)

> **Correção v2 (5 jul/2026):** este pacote corrige um `LazyInitializationException`
> real que causava 500 em `/api/v1/auth/me` e `/api/v1/dashboard/summary` depois
> do primeiro login (ver seção 7.1). Se você baixou o zip antes desta data,
> substitua pelo menos os arquivos `UserRepository.java` e
> `GlobalExceptionHandler.java`.

ERP operacional para empresas de serviços sob demanda. Projeto Integrador.

Este pacote entrega a Sprint 1 do roadmap: setup do projeto, banco criado a
partir do DDL validado na Etapa 2.2, e autenticação funcionando de ponta a
ponta (login → JWT → rota protegida → dados reais do banco).

---

## 1. O que está funcionando nesta entrega

- Login com e-mail e senha, retornando um JWT válido
- Rota protegida `/api/v1/auth/me` (restaura sessão ao recarregar a página)
- Rota protegida `/api/v1/dashboard/summary` (prova de leitura real do banco,
  isolada por `company_id` do usuário autenticado)
- Frontend em React com tela de Login e Dashboard, redirecionamento automático
  se não autenticado
- Banco PostgreSQL criado automaticamente a partir do `flowops_ddl.sql`
  (Etapa 2.2), populado com dados de demonstração

Os módulos de Clientes, WorkOrders (CRUD completo) e Etapas chegam nas
Sprints 2, 3 e 4, conforme o Roadmap documentado no Notion.

---

## 2. Pré-requisitos

Você só precisa de **uma** destas duas opções:

### Opção A — Docker (recomendado, mais simples)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado
  (inclui o Docker Compose)

### Opção B — Ambiente local (sem Docker)
- Java 21 ([Adoptium Temurin](https://adoptium.net/))
- Maven 3.9+ (ou use o `./mvnw` incluso, se você adicionar o wrapper)
- Node.js 20+ e npm
- PostgreSQL 16 instalado localmente

Este guia foca na **Opção A**, que é a forma mais rápida de ter tudo rodando.
A Opção B é detalhada na seção 6, para quem quiser depurar cada parte
separadamente durante o desenvolvimento.

---

## 3. Passo a passo — Docker Compose

### 3.1. Extraia o projeto e entre na pasta

```bash
cd flowops
```

### 3.2. Crie o arquivo de variáveis de ambiente

```bash
cp .env.example .env
```

Abra o `.env` e, se quiser, troque o `JWT_SECRET` por uma chave própria:

```bash
openssl rand -hex 32
```

Para rodar localmente pela primeira vez, os valores padrão do
`.env.example` já funcionam sem alterar nada.

### 3.3. Suba os três serviços

```bash
docker compose up --build
```

Isso vai:
1. Criar o container do PostgreSQL 16 e rodar `flowops_ddl.sql` seguido de
   `seed.sql` automaticamente (só na primeira vez que o volume é criado)
2. Buildar a imagem do backend (Maven + Spring Boot) e subir na porta `8080`
3. Buildar a imagem do frontend (Vite dev server) e subir na porta `5173`

A primeira execução demora alguns minutos (download de dependências Maven e
npm). As próximas são bem mais rápidas graças ao cache das camadas do Docker.

### 3.4. Confirme que os três serviços estão de pé

```bash
docker compose ps
```

Espere ver `db`, `backend` e `frontend` com status `running` (ou `healthy`
no caso do `db`).

Se o `backend` continuar reiniciando, veja os logs:

```bash
docker compose logs backend --tail=100
```

### 3.5. Acesse o sistema

Abra **http://localhost:5173** no navegador. Você deve ver a tela de login.

**Credenciais de demonstração** (criadas pelo `seed.sql`):

| E-mail | Senha | Role |
|---|---|---|
| `admin@flowops.dev` | `FlowOps@123` | ADMIN_EMPRESA |
| `operador@flowops.dev` | `FlowOps@123` | OPERADOR |
| `tecnico@flowops.dev` | `FlowOps@123` | TECNICO |

Depois do login, você deve cair no Dashboard e ver os contadores reais de
WorkOrders vindos do banco (2 WorkOrders de exemplo, uma em
`EM_EXECUCAO` e outra em `SOLICITACAO_RECEBIDA`).

### 3.6. Para encerrar

```bash
docker compose down
```

Isso mantém o volume do banco (`flowops_pgdata`) intacto. Para apagar tudo
e recomeçar do zero (recriar schema + seed na próxima subida):

```bash
docker compose down -v
```

---

## 4. Testando a API diretamente (sem o frontend)

Útil para confirmar que o backend está respondendo corretamente, isolado do
React.

```bash
# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@flowops.dev","password":"FlowOps@123"}'
```

A resposta deve trazer um `accessToken`. Copie esse valor e use nas próximas
chamadas:

```bash
TOKEN="cole_o_token_aqui"

curl http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN"

curl http://localhost:8080/api/v1/dashboard/summary \
  -H "Authorization: Bearer $TOKEN"
```

Sem o header `Authorization`, ambas as rotas devem retornar `401`.

---

## 5. Estrutura do projeto

```
flowops/
├── backend/                  Spring Boot (Java 21)
│   ├── src/main/java/com/flowops/
│   │   ├── config/            SecurityConfig (JWT, CORS, sessão stateless)
│   │   ├── security/           JwtService, JwtAuthenticationFilter
│   │   ├── entity/              Entidades JPA (uma por tabela do DDL)
│   │   ├── repository/          Interfaces Spring Data JPA
│   │   ├── service/             AuthService, CustomUserDetailsService
│   │   ├── controller/          AuthController, DashboardController
│   │   ├── dto/                  Records de entrada/saída da API
│   │   └── exception/            GlobalExceptionHandler
│   ├── src/main/resources/application.yml
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/                 React 18 + Vite + Tailwind
│   ├── src/
│   │   ├── contexts/AuthContext.jsx   Estado global de login
│   │   ├── services/api.js             Cliente Axios com JWT automático
│   │   ├── components/ProtectedRoute.jsx
│   │   └── pages/{Login,Dashboard}.jsx
│   └── Dockerfile
│
├── db/
│   ├── flowops_ddl.sql        Schema completo (Etapa 2.2, já validado)
│   └── seed.sql                Dados de demonstração
│
├── docker-compose.yml
├── .env.example
└── README.md                  Este arquivo
```

---

## 6. Rodando sem Docker (desenvolvimento local)

Use esta opção se quiser rodar o backend na sua IDE (IntelliJ, VS Code) com
debug, hot-reload mais rápido, etc.

### 6.1. Banco de dados

Instale o PostgreSQL 16 localmente e crie o banco:

```bash
createdb flowops
psql -d flowops -f db/flowops_ddl.sql
psql -d flowops -f db/seed.sql
```

### 6.2. Backend

```bash
cd backend

# Variáveis de ambiente mínimas (ou configure na sua IDE)
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=flowops
export DB_USER=flowops
export DB_PASSWORD=flowops
export JWT_SECRET=troque-esta-chave-em-producao-minimo-256-bits-0123456789abcdef

mvn spring-boot:run
```

A API sobe em `http://localhost:8080`.

### 6.3. Frontend

```bash
cd frontend
npm install
npm run dev
```

O Vite sobe em `http://localhost:5173` e já aponta para
`http://localhost:8080/api/v1` por padrão (ver `.env.example` do frontend).

---

## 7. Decisões técnicas relevantes para a defesa do PI

Estas são as escolhas que vale a pena saber explicar na apresentação:

- **Timestamps controlados pelo banco, não pelo Java.** As colunas
  `created_at`/`updated_at` são `insertable = false, updatable = false` nas
  entidades JPA — o valor vem do `DEFAULT now()` e do trigger
  `set_updated_at()` do PostgreSQL (Etapa 2.2), não de lógica duplicada na
  aplicação. Evita divergência entre o horário do banco e o do servidor de
  aplicação.
- **UUID gerado no Java, não no banco.** Embora a coluna tenha
  `DEFAULT gen_random_uuid()`, o `@PrePersist` gera o UUID antes do insert
  para que o objeto já tenha o valor correto em memória sem precisar de um
  `refresh()` depois de salvar.
- **JWT stateless com claims mínimos.** O payload carrega só `userId`,
  `companyId` e `role` — o suficiente para autorização, sem inflar o token
  com dados que mudam com frequência (isso ficaria desatualizado até o
  token expirar).
- **UserDetails implementado diretamente na entidade `User`.** Evita uma
  classe adaptadora extra; para o tamanho atual do domínio, o acoplamento
  entre entidade JPA e contrato do Spring Security é aceitável.
- **`ddl-auto: validate`, nunca `update` ou `create`.** O schema é
  responsabilidade exclusiva do `flowops_ddl.sql`. O Hibernate apenas
  confirma que o mapeamento das entidades bate com as tabelas existentes —
  se alguém alterar uma entidade sem atualizar o DDL, a aplicação falha ao
  iniciar, em vez de alterar o banco silenciosamente.

---

## 7.1. Bug corrigido nesta versão — LazyInitializationException

Se você testou a v1 e viu `500 Internal Server Error` em `/api/v1/auth/me`
e `/api/v1/dashboard/summary` (mas o `/api/v1/auth/login` funcionava normal),
era este bug. Vale entender porque é exatamente o tipo de decisão de
arquitetura que uma banca gosta de ver explicada.

**Causa raiz:** `User.company` é `@ManyToOne(fetch = FetchType.LAZY)`. O
`JwtAuthenticationFilter` carrega o `User` **fora de qualquer transação**
(filtros rodam antes do `Controller`/`Service`). Como `spring.jpa.open-in-view`
está propositalmente `false` (é a prática recomendada — evita abrir uma sessão
Hibernate por toda a duração da requisição HTTP), a sessão que carregou o
`User` no filtro já estava fechada quando o `Controller` tentava acessar
`user.getCompany().getId()`. Resultado: `LazyInitializationException`.

**Por que virou 500 em vez de um erro mais claro:** o `GlobalExceptionHandler`
tinha um `catch (Exception ex)` genérico que devolvia sempre a mesma mensagem
("Erro interno inesperado") sem nunca logar a exceção original — então o
`LazyInitializationException` real ficava invisível nos logs do Docker.

**A correção (duas partes):**

1. `UserRepository.findByEmailAndActiveTrue` ganhou
   `@EntityGraph(attributePaths = "company")`, forçando o Hibernate a
   carregar `company` na mesma query via `JOIN`, independente de transação.
   Isso resolve o filtro sem precisar tornar o relacionamento `EAGER` de
   forma global (o que penalizaria toda consulta de `User` que não precisa
   da empresa).
2. `GlobalExceptionHandler` agora loga toda exceção genérica com
   `log.error("Erro interno inesperado", ex)` antes de responder 500 —
   qualquer bug futuro do mesmo tipo aparece imediatamente em
   `docker compose logs backend`.

---

## 7.2. Outro problema encontrado: Java 25 no Dockerfile/pom.xml

Se você (ou alguma sugestão de outra ferramenta) alterou o `Dockerfile` e o
`pom.xml` do backend para usar `temurin-25` / `java.version=25`, o build vai
falhar com:

```
[ERROR] Failed to execute goal org.springframework.boot:spring-boot-maven-plugin:3.3.4:repackage
Unsupported class file major version 69
```

O plugin de repackage do Spring Boot 3.3.4 usa uma versão do ASM que ainda
não reconhece bytecode gerado pelo JDK 25 (major version 69). A correção é
manter `Dockerfile` e `pom.xml` em **Java 21 (LTS)**, que é o que este
pacote usa — Spring Boot 3.3.x é validado e testado contra o 21, não o 25.

Se o build falhar assim, o `docker compose up --build` para de tentar
recriar a imagem nova, mas os containers antigos (de uma build anterior
bem-sucedida) continuam rodando — por isso o `docker ps` pode mostrar tudo
"healthy" mesmo com o build mais recente quebrado. Rode
`docker compose logs backend --tail=200` para confirmar se o container em
pé é realmente da build mais recente.

---

## 7.3. `docker compose logs` não encontra o compose file

Se aparecer `no configuration file provided: not found`, o comando foi
rodado fora da pasta do projeto (por exemplo, em `C:\Windows\System32`).
O `docker compose` procura um `docker-compose.yml` no diretório atual.
Entre na pasta do projeto antes de rodar qualquer comando:

```powershell
cd caminho\para\flowops
docker compose logs backend
```

---

## 8. Troubleshooting

**`backend` fica em loop de restart**
Veja os logs (`docker compose logs backend`). Na grande maioria das vezes é
o backend subindo antes do Postgres aceitar conexões — o `depends_on` com
`condition: service_healthy` já deveria evitar isso, mas se acontecer, rode
`docker compose up` novamente sem `--build`.

**Erro 401 em todas as rotas, mesmo depois do login**
Confira se o `JWT_SECRET` usado para gerar o token é o mesmo configurado no
backend no momento da validação — isso normalmente acontece se você mudou o
`.env` e não recriou o container (`docker compose up --build backend`).

**Tela de login não carrega / erro de CORS no console do navegador**
Confirme que `CORS_ALLOWED_ORIGINS` no `.env` bate exatamente com a URL que
você está usando no navegador (incluindo `http://` e a porta).

**Quero recriar o hash de senha de demonstração**
```python
import bcrypt
print(bcrypt.hashpw(b"SuaNovaSenha", bcrypt.gensalt(rounds=10)).decode())
```
Substitua o valor de `password_hash` no `seed.sql` (ou direto no banco) pelo
hash gerado.

---

## 9. Próximo passo

Sprint 2 do Roadmap: CRUD completo do módulo Clientes (backend + frontend),
usando exatamente os mesmos padrões de Controller → Service → Repository já
estabelecidos aqui no módulo de Auth.
