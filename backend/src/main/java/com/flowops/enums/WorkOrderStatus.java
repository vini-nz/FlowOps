package com.flowops.enums;

/**
 * State machine da WorkOrder (aggregate root - decisao D-02).
 * Transicoes validas sao controladas na camada Service, nunca via
 * atribuicao direta de status pelo Controller ou pelo Repository.
 */
public enum WorkOrderStatus {
    SOLICITACAO_RECEBIDA,
    ORCAMENTO_GERADO,
    AGUARDANDO_APROVACAO,
    APROVADO,
    RECUSADO,
    EM_EXECUCAO,
    ENTREGUE,
    FINALIZADO
}
