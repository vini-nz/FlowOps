# ADR-0003 — Status derivado de fato, não rótulo livre

**Status:** Aceito
**Data:** 25 de julho de 2026

## Contexto

Até a V2.3 as duas state machines do sistema (`WorkOrderStatusTransitions` e
`StepStatusTransitions`) validavam apenas *a forma* da transição — "de A dá
para ir para B?" — e nunca *se a realidade justificava* aquela transição.
Orçamento, etapas e status da WorkOrder evoluíam em trilhos paralelos que
não se consultavam. Isso produzia estados que o negócio não reconhece:

1. **Beco sem saída irreversível.** `SOLICITACAO_RECEBIDA → ORCAMENTO_GERADO`
   era uma transição manual válida, mas `BudgetService.create` exige
   `SOLICITACAO_RECEBIDA`. Quem avançasse o status pela tela deixava a
   WorkOrder afirmando ter orçamento **sem ter, e sem nunca mais poder
   criar um**. Reproduzido antes da correção: após o avanço manual, criar
   orçamento retornava 409 e o PDF retornava 404, permanentemente.
2. Etapas podiam ser executadas com a WorkOrder ainda em
   `SOLICITACAO_RECEBIDA` — trabalho registrado antes de existir orçamento
   aprovado que o autorizasse.
3. Etapas podiam ser executadas fora de ordem: era possível concluir
   "Instalação" com "Produção" e "Acabamento" ainda em `PENDENTE`.
4. O status podia caminhar até `FINALIZADO` sem que uma única etapa saísse
   de `PENDENTE` — o sistema declarava entrega de um trabalho que nunca
   começou.

## Decisão

**O status deixa de ser um rótulo que o usuário escolhe e passa a ser o
reflexo de fatos registrados.** Concretamente:

1. As transições válidas passam a ter dois conjuntos
   (`WorkOrderStatusTransitions`): `isValid` (a máquina completa) e
   `isManual` (o que o usuário dispara diretamente). Toda a fase comercial
   — `ORCAMENTO_GERADO`, `AGUARDANDO_APROVACAO`, `APROVADO`, `RECUSADO` —
   sai do conjunto manual e só é aplicada pelo sistema, via
   `WorkOrderService.applyDerivedStatus`, como consequência de criar ou
   decidir um orçamento.
2. Etapa só é editável com a WorkOrder em `APROVADO` ou `EM_EXECUCAO`.
3. Etapa só inicia (`EM_ANDAMENTO`) se todas as anteriores por `step_order`
   estiverem `CONCLUIDA`. Bloquear (`BLOQUEADA`) uma etapa futura continua
   livre — sinalizar impedimento não é começar o trabalho.
4. `EM_EXECUCAO → ENTREGUE` exige todas as etapas concluídas.
5. Iniciar a primeira etapa move `APROVADO → EM_EXECUCAO` automaticamente:
   começar a trabalhar *é* o fato que define "em execução".

A fase de execução (`EM_EXECUCAO`, `ENTREGUE`, `FINALIZADO`) permanece
manual de propósito: iniciar, entregar e finalizar são atos do mundo real
que ninguém consegue inferir do banco.

## Alternativas consideradas

- **Permitir tudo e apenas alertar na tela.** Descartada: a trava precisa
  estar no backend, senão a API continua aceitando o estado inconsistente e
  o problema volta pela primeira integração externa.
- **Deixar `BudgetService.create` aceitar também `ORCAMENTO_GERADO`.**
  Resolveria só o sintoma nº 1 e manteria o status como rótulo livre — a
  WorkOrder continuaria podendo chegar a `FINALIZADO` sem orçamento nem
  execução.
- **Grafo de dependências entre etapas (`depends_on_step_id`).** É o que
  "Etapas Avançadas" prevê na V3, e resolve etapas paralelas/opcionais —
  que ainda não existem. Aqui bastou a ordem sequencial por `step_order`,
  sem coluna nova. Quando o grafo chegar, ele substitui
  `assertPreviousStepsCompleted`, não convive com ele.

## Consequências

- A tela não oferece mais botões de avanço na fase comercial; no lugar
  aparece a ação que de fato move o status ("Crie o orçamento para
  avançar"). Sem isso a interface pareceria travada sem explicação.
- Mensagens de erro passam a dizer *qual ação* provoca o status pretendido,
  em vez de um "transição inválida" seco.
- WorkOrder sem etapas (empresa sem `workflow_template` padrão) continua
  podendo ser entregue — a trava vale para quem tem execução a cumprir.
- Dados já gravados em estado inconsistente **não** foram migrados: o banco
  atual só tem dados de demonstração (decisão registrada com o responsável
  pelo projeto). Se o sistema for para produção com dados reais antes de
  outra revisão desse tipo, uma migração de saneamento passa a ser
  necessária.

## Quando revisitar

Se surgir um caso de negócio legítimo de execução fora de ordem (etapas
paralelas, etapa opcional pulada) ou de entrega parcial, a regra sequencial
deve dar lugar ao grafo de dependências da V3 — mas mantendo o princípio: o
status continua sendo consequência de fatos, não escolha livre.
