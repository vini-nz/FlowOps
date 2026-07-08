package com.flowops.service;

import com.flowops.enums.WorkOrderStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.flowops.enums.WorkOrderStatus.*;

/**
 * Transições válidas da state machine da WorkOrder (decisão D-02, documentada
 * em Negócio e Domínio no Notion). Mantida separada do WorkOrderService de
 * propósito: a regra de negócio "quais transições fazem sentido" não deveria
 * estar misturada com a lógica de persistência.
 */
public final class WorkOrderStatusTransitions {

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> ALLOWED = new EnumMap<>(WorkOrderStatus.class);

    static {
        ALLOWED.put(SOLICITACAO_RECEBIDA, EnumSet.of(ORCAMENTO_GERADO));
        ALLOWED.put(ORCAMENTO_GERADO, EnumSet.of(AGUARDANDO_APROVACAO));
        ALLOWED.put(AGUARDANDO_APROVACAO, EnumSet.of(APROVADO, RECUSADO));
        ALLOWED.put(APROVADO, EnumSet.of(EM_EXECUCAO));
        ALLOWED.put(EM_EXECUCAO, EnumSet.of(ENTREGUE));
        ALLOWED.put(ENTREGUE, EnumSet.of(FINALIZADO));
        // RECUSADO e FINALIZADO sao estados terminais: nenhuma transicao a partir deles.
        ALLOWED.put(RECUSADO, EnumSet.noneOf(WorkOrderStatus.class));
        ALLOWED.put(FINALIZADO, EnumSet.noneOf(WorkOrderStatus.class));
    }

    private WorkOrderStatusTransitions() {
    }

    public static boolean isValid(WorkOrderStatus from, WorkOrderStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<WorkOrderStatus> nextStatesFrom(WorkOrderStatus from) {
        return ALLOWED.getOrDefault(from, Set.of());
    }
}
