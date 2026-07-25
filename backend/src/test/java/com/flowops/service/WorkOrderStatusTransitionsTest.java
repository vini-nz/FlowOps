package com.flowops.service;

import org.junit.jupiter.api.Test;

import static com.flowops.enums.WorkOrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a separação entre transições manuais e derivadas introduzida na V2.4
 * (ADR-0003). O caso central é o beco sem saída que existia até a V2.3:
 * avançar manualmente para ORCAMENTO_GERADO tornava a criação do orçamento
 * impossível para sempre, porque BudgetService.create exige
 * SOLICITACAO_RECEBIDA.
 */
class WorkOrderStatusTransitionsTest {

    @Test
    void commercialPhaseIsNeverManual() {
        assertThat(WorkOrderStatusTransitions.isManual(SOLICITACAO_RECEBIDA, ORCAMENTO_GERADO)).isFalse();
        assertThat(WorkOrderStatusTransitions.isManual(ORCAMENTO_GERADO, AGUARDANDO_APROVACAO)).isFalse();
        assertThat(WorkOrderStatusTransitions.isManual(AGUARDANDO_APROVACAO, APROVADO)).isFalse();
        assertThat(WorkOrderStatusTransitions.isManual(AGUARDANDO_APROVACAO, RECUSADO)).isFalse();
    }

    @Test
    void commercialPhaseIsStillValidWhenAppliedBySystem() {
        // Nao e proibida: e proibida ao usuario. O BudgetService continua
        // podendo aplica-la via applyDerivedStatus.
        assertThat(WorkOrderStatusTransitions.isValid(SOLICITACAO_RECEBIDA, ORCAMENTO_GERADO)).isTrue();
        assertThat(WorkOrderStatusTransitions.isValid(ORCAMENTO_GERADO, AGUARDANDO_APROVACAO)).isTrue();
        assertThat(WorkOrderStatusTransitions.isValid(AGUARDANDO_APROVACAO, APROVADO)).isTrue();
        assertThat(WorkOrderStatusTransitions.isValid(AGUARDANDO_APROVACAO, RECUSADO)).isTrue();
    }

    @Test
    void executionPhaseStaysManual() {
        assertThat(WorkOrderStatusTransitions.isManual(APROVADO, EM_EXECUCAO)).isTrue();
        assertThat(WorkOrderStatusTransitions.isManual(EM_EXECUCAO, ENTREGUE)).isTrue();
        assertThat(WorkOrderStatusTransitions.isManual(ENTREGUE, FINALIZADO)).isTrue();
    }

    @Test
    void manualIsAlwaysASubsetOfValid() {
        for (var from : values()) {
            assertThat(WorkOrderStatusTransitions.isValid(from, from)).isFalse();
            for (var to : WorkOrderStatusTransitions.manualNextStatesFrom(from)) {
                assertThat(WorkOrderStatusTransitions.isValid(from, to))
                        .as("%s → %s é manual mas não é válida", from, to)
                        .isTrue();
            }
        }
    }

    @Test
    void terminalStatesAllowNothing() {
        assertThat(WorkOrderStatusTransitions.manualNextStatesFrom(RECUSADO)).isEmpty();
        assertThat(WorkOrderStatusTransitions.manualNextStatesFrom(FINALIZADO)).isEmpty();
        assertThat(WorkOrderStatusTransitions.nextStatesFrom(RECUSADO)).isEmpty();
        assertThat(WorkOrderStatusTransitions.nextStatesFrom(FINALIZADO)).isEmpty();
    }

    @Test
    void noManualPathReachesCommercialPhaseFromAnyState() {
        // Garante que o beco sem saida nao volta por outro caminho: nenhum
        // estado pode chegar manualmente a ORCAMENTO_GERADO.
        for (var from : values()) {
            assertThat(WorkOrderStatusTransitions.manualNextStatesFrom(from))
                    .as("nenhum estado deve alcancar a fase comercial manualmente, mas %s alcanca", from)
                    .doesNotContain(ORCAMENTO_GERADO, AGUARDANDO_APROVACAO, APROVADO, RECUSADO);
        }
    }
}
