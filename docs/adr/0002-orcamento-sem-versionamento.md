# ADR-0002 — Orçamento sem versionamento (um por WorkOrder)

**Status:** Aceito
**Data:** 12 de julho de 2026

## Contexto

O Backlog Detalhado (item 1 — Orçamentos e Catálogo) descreve a tabela
`budgets` "com versionamento", sugerindo suporte a múltiplas propostas
sucessivas para a mesma WorkOrder (ex: recotação após recusa, ajuste de
valores antes da aprovação).

A state machine da WorkOrder (D-02, `WorkOrderStatusTransitions`) trata
`RECUSADO` como estado terminal — não existe transição de volta para
`ORCAMENTO_GERADO`. Ou seja, o próprio fluxo de negócio já implementado não
permite reabrir uma WorkOrder recusada para gerar um novo orçamento.

## Decisão

**Um orçamento por WorkOrder, sem versionamento.** A tabela `budgets` tem
`work_order_id` com constraint `UNIQUE`. Enquanto o orçamento está em
`RASCUNHO`, itens podem ser livremente adicionados/removidos e o total é
recalculado — isso cobre o caso de "ajustar antes de decidir", que é o
cenário real coberto pelos critérios de aceitação do item 1. Após
`APROVADO`/`RECUSADO`, o orçamento fica congelado.

## Motivação

1. **A state machine já implementada não sustenta o caso de uso que o
   versionamento resolveria.** Sem uma transição de `RECUSADO` de volta
   para `ORCAMENTO_GERADO`, não há momento no fluxo atual em que uma segunda
   versão do orçamento faria sentido ser criada pelo sistema.
2. **Os critérios de aceitação do item 1 não exigem histórico de versões** —
   apenas criar orçamento, adicionar itens, calcular total e refletir
   aprovação/recusa no status da WorkOrder. Implementar versionamento agora
   seria construir para um requisito hipotético, não documentado.
3. **RASCUNHO já cobre o caso real de ajuste** (edição de itens antes da
   decisão), sem a complexidade extra de gerenciar múltiplas linhas
   históricas, qual delas é "a atual", e como isso se relaciona com o PDF
   gerado no item 2 (V2.2).

## Quando revisitar

Se a state machine da WorkOrder ganhar uma transição de `RECUSADO` para um
novo ciclo de orçamento (reabertura), ou se surgir um requisito real de
negócio para reenviar propostas com valores diferentes para o mesmo
cliente/WorkOrder, o versionamento deve ser reavaliado — nesse momento,
`work_order_id` deixaria de ser `UNIQUE` e passaria a existir uma coluna
`version` com um critério explícito de "orçamento vigente".
