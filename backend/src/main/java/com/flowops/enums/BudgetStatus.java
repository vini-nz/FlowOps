package com.flowops.enums;

/**
 * Status do orcamento (V2.1). RASCUNHO permite editar itens; APROVADO/
 * RECUSADO sao terminais (ver ADR-0002 - sem versionamento).
 */
public enum BudgetStatus {
    RASCUNHO,
    APROVADO,
    RECUSADO
}
