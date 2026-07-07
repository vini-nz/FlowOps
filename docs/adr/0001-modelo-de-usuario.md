# ADR-0001 — Modelo de identidade do usuário

**Status:** Aceito
**Data:** 6 de julho de 2026

## Contexto

Ao testar a Sprint 2, foi identificado que o modelo atual — cada usuário
pertence a exatamente uma empresa (`users.company_id`) — impede cenários de
negócio legítimos em sistemas multiempresa reais:

- Um consultor, contador ou equipe de suporte que precisa acessar várias
  empresas sem ter um login diferente para cada uma
- Grupos com matriz e filiais, onde o mesmo usuário deveria transitar entre
  unidades sem duplicar cadastro
- Franquias, onde a mesma pessoa pode atuar em mais de uma unidade

O modelo usado por SaaS profissionais (Slack, Notion, Google Workspace,
Atlassian) resolve isso com uma identidade de usuário **global**, associada
a empresas através de uma tabela de junção (`user_company`), com um passo de
"escolha de empresa" após o login quando o usuário tem acesso a mais de uma.

## Decisão

**Não migrar agora.** O FlowOps mantém o modelo atual: cada usuário pertence
a exatamente uma empresa.

## Motivação

1. **Nenhum requisito de negócio documentado exige isso hoje.** Os casos de
   uso e personas definidos em Negócio e Domínio (Notion) não incluem
   consultores, contadores ou suporte multiempresa — esses cenários foram
   levantados como hipóteses ao testar o sistema, não como requisito real
   do MVP.
2. **O custo da migração é alto e se propaga por praticamente todo o
   backend**: Spring Security, geração de JWT, `AuthService`, RBAC,
   auditoria (`created_by_id` deixaria de apontar para um usuário
   "pertencente" a uma empresa), seed de dados e testes. Não é uma mudança
   isolada em uma tabela.
3. **Esse é exatamente o tipo de decisão que vale mais documentar
   conscientemente do que implementar apressado.** Adiar com justificativa
   registrada demonstra mais maturidade de engenharia do que migrar sem um
   requisito real por trás.

## Alternativa considerada para o caso de suporte

Se e quando a necessidade de acesso multiempresa por um único usuário
surgir — por exemplo, para permitir que a equipe de suporte entre em
qualquer empresa para diagnóstico — a solução recomendada **não** é migrar
todo o modelo de usuário. É introduzir um mecanismo de acesso temporário
similar ao "impersonate" usado por Microsoft 365, Zendesk e Salesforce:

- Um usuário com papel de suporte, que não pertence a nenhuma empresa
- Uma tela (ou endpoint) onde ele escolhe qual empresa quer acessar
- O backend emite um JWT de curta duração com o `company_id` escolhido
- Nenhuma duplicação de cadastro, nenhuma mudança no modelo principal

## Quando revisitar

Esta decisão deve ser reaberta se o FlowOps ganhar um requisito real de:
- Um usuário atuando em mais de uma empresa como parte do fluxo normal de
  trabalho (não administração/suporte)
- Módulo de franquias ou matriz/filiais
- Um modelo comercial onde a mesma pessoa gerencia múltiplos clientes do
  sistema (ex: um contador que usa o FlowOps para vários clientes seus)

Quando isso acontecer, a migração para `User` global + `UserCompany` deve
ser tratada como uma evolução arquitetural planejada — não uma correção de
última hora.
