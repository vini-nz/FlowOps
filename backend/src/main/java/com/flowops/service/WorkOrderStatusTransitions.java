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
 * <p>
 * A partir da V2.4 o conjunto de transições válidas é dividido em dois (ver
 * ADR-0003 — "status derivado de fato"):
 * <ul>
 *   <li>{@link #isValid} — a máquina completa: toda transição que o domínio
 *       reconhece, independente de quem a provocou.</li>
 *   <li>{@link #isManual} — o subconjunto que um usuário pode disparar
 *       diretamente por {@code PATCH /work-orders/{uuid}/status}.</li>
 * </ul>
 * O que está fora de {@code isManual} só acontece como <em>consequência</em>
 * de um fato registrado em outro lugar: criar um orçamento leva a
 * {@code ORCAMENTO_GERADO}, decidi-lo leva a {@code APROVADO}/
 * {@code RECUSADO}. Antes dessa separação as duas regras se contradiziam —
 * era possível avançar manualmente para {@code ORCAMENTO_GERADO} e, a partir
 * daí, o orçamento nunca mais podia ser criado (BudgetService.create exige
 * {@code SOLICITACAO_RECEBIDA}), deixando a WorkOrder num estado que afirmava
 * ter orçamento sem ter, de forma irreversível.
 */
public final class WorkOrderStatusTransitions {

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> ALLOWED = new EnumMap<>(WorkOrderStatus.class);

    /**
     * Transições disparáveis diretamente pelo usuário. As demais existem em
     * {@link #ALLOWED}, mas só o sistema as aplica, a partir de um fato:
     * {@code SOLICITACAO_RECEBIDA → ORCAMENTO_GERADO} (orçamento criado),
     * {@code ORCAMENTO_GERADO → AGUARDANDO_APROVACAO} e
     * {@code AGUARDANDO_APROVACAO → APROVADO|RECUSADO} (orçamento decidido).
     */
    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> MANUAL = new EnumMap<>(WorkOrderStatus.class);

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

        // A fase comercial inteira e consequencia de acoes no orcamento.
        MANUAL.put(SOLICITACAO_RECEBIDA, EnumSet.noneOf(WorkOrderStatus.class));
        MANUAL.put(ORCAMENTO_GERADO, EnumSet.noneOf(WorkOrderStatus.class));
        MANUAL.put(AGUARDANDO_APROVACAO, EnumSet.noneOf(WorkOrderStatus.class));
        // A fase de execucao continua sendo decisao humana: iniciar, entregar
        // e finalizar sao atos do mundo real que ninguem infere do banco.
        // (APROVADO -> EM_EXECUCAO tambem acontece sozinho ao iniciar a
        // primeira etapa, mas segue manual para WorkOrders sem workflow.)
        MANUAL.put(APROVADO, EnumSet.of(EM_EXECUCAO));
        MANUAL.put(EM_EXECUCAO, EnumSet.of(ENTREGUE));
        MANUAL.put(ENTREGUE, EnumSet.of(FINALIZADO));
        MANUAL.put(RECUSADO, EnumSet.noneOf(WorkOrderStatus.class));
        MANUAL.put(FINALIZADO, EnumSet.noneOf(WorkOrderStatus.class));
    }

    private WorkOrderStatusTransitions() {
    }

    public static boolean isValid(WorkOrderStatus from, WorkOrderStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** Transição que o usuário pode disparar diretamente pela API/tela. */
    public static boolean isManual(WorkOrderStatus from, WorkOrderStatus to) {
        return MANUAL.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<WorkOrderStatus> nextStatesFrom(WorkOrderStatus from) {
        return ALLOWED.getOrDefault(from, Set.of());
    }

    /** Usado pela tela para oferecer só os botões que de fato funcionam. */
    public static Set<WorkOrderStatus> manualNextStatesFrom(WorkOrderStatus from) {
        return MANUAL.getOrDefault(from, Set.of());
    }
}
