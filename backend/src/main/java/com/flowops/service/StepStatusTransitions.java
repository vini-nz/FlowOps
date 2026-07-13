package com.flowops.service;

import com.flowops.enums.StepStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.flowops.enums.StepStatus.*;

/**
 * Transicoes validas da state machine de uma etapa (WorkOrderStep), analoga a
 * WorkOrderStatusTransitions (Sprint 3): sem dependencia de Spring, so enum e
 * colecoes puras do Java, testavel isoladamente com javac.
 *
 * Diferenca deliberada em relacao a WorkOrderStatusTransitions: aqui
 * isValid(x, x) e verdadeiro (self-transition permitida) para todo status
 * nao-terminal. Isso existe porque WorkOrderStepService.updateStatus tambem e
 * o unico caminho para CU-022 (registrar observacao na etapa) - um Tecnico
 * deve poder salvar uma nota sem ser obrigado a mudar o status, e bloquear a
 * self-transition tornaria isso impossivel sem duplicar o endpoint.
 * CONCLUIDA continua terminal mesmo para si mesma: uma etapa concluida nao se
 * "atualiza" mais - reabrir exige um novo registro, nao um PATCH.
 */
public final class StepStatusTransitions {

    private static final Map<StepStatus, Set<StepStatus>> ALLOWED = new EnumMap<>(StepStatus.class);

    static {
        ALLOWED.put(PENDENTE, EnumSet.of(PENDENTE, EM_ANDAMENTO, BLOQUEADA));
        ALLOWED.put(EM_ANDAMENTO, EnumSet.of(EM_ANDAMENTO, CONCLUIDA, BLOQUEADA));
        ALLOWED.put(BLOQUEADA, EnumSet.of(BLOQUEADA, PENDENTE, EM_ANDAMENTO));
        // CONCLUIDA e terminal: nenhuma transicao a partir dela, nem para si mesma.
        ALLOWED.put(CONCLUIDA, EnumSet.noneOf(StepStatus.class));
    }

    private StepStatusTransitions() {
    }

    public static boolean isValid(StepStatus from, StepStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<StepStatus> nextStatesFrom(StepStatus from) {
        return ALLOWED.getOrDefault(from, Set.of());
    }
}
