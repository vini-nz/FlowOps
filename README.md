# FlowOps

ERP operacional para empresas de serviços sob demanda.

FlowOps é um sistema desenvolvido como Projeto Integrador com o objetivo de
aplicar práticas modernas de engenharia de software na construção de um ERP
para empresas que executam serviços personalizados e operações sob demanda
(marcenarias, instaladores, empresas de reforma, entre outras).

O projeto foi pensado tanto como estudo acadêmico quanto como portfólio
técnico, usando tecnologias amplamente empregadas no mercado.

---

## Funcionalidades

- Autenticação com JWT
- Controle de acesso por perfis (RBAC)
- Gestão de clientes
- Gestão de WorkOrders, com state machine de status e RBAC aplicado
- Workflow baseado em etapas *(em desenvolvimento)*
- Dashboard operacional
- Arquitetura REST, multi-tenant por empresa
- Banco PostgreSQL com schema versionado
- Ambiente containerizado com Docker

## Tecnologias

**Backend** — Java 21 · Spring Boot 3 · Spring Security · Spring Data JPA · PostgreSQL

**Frontend** — React 18 · Vite · Tailwind CSS · Axios

**Infraestrutura** — Docker · Docker Compose

---

## Executando o projeto

Pré-requisito: [Docker Desktop](https://www.docker.com/products/docker-desktop/)

```bash
git clone <url-do-repositorio>
cd flowops
cp .env.example .env
docker compose up --build
```

A aplicação fica disponível em:

| Serviço | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend (API) | http://localhost:8080 |

Guia completo de instalação, variáveis de ambiente e solução de problemas em
[`docs/setup.md`](docs/setup.md).

---

## Estrutura

```
flowops/
├── backend/     Spring Boot (Java 21)
├── frontend/    React + Vite + Tailwind
├── db/          Schema PostgreSQL e dados de demonstração
├── docs/        Documentação técnica
└── docker-compose.yml
```

## Documentação

| Documento | Conteúdo |
|---|---|
| [`docs/setup.md`](docs/setup.md) | Instalação detalhada, variáveis de ambiente, execução local sem Docker, solução de problemas |
| [`docs/architecture.md`](docs/architecture.md) | Decisões arquiteturais e o porquê de cada uma |
| [`docs/adr/`](docs/adr/) | Registros formais de decisões de arquitetura (ADRs) |
| [`docs/api.md`](docs/api.md) | Referência dos endpoints da API |
| [`CHANGELOG.md`](CHANGELOG.md) | Histórico de versões e correções |

Documentação de produto (visão, domínio de negócio, casos de uso, roadmap
completo) é mantida separadamente no Notion do projeto.

## Roadmap

- [x] Sprint 1 — Fundação (auth, Docker, banco)
- [x] Sprint 2 — Gestão de Clientes
- [x] Sprint 3 — WorkOrders
- [ ] Sprint 4 — Etapas e Dashboard operacional
- [ ] Sprint 5 — Deploy

---

## Objetivo

Mais do que um ERP funcional, o FlowOps busca aplicar conceitos de
arquitetura em camadas, modelagem de domínio, segurança, APIs REST, Docker
e boas práticas de desenvolvimento — simulando a construção de um software
de nível profissional dentro de um projeto acadêmico.
