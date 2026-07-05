package com.flowops.enums;

/**
 * Roles do MVP. Os valores devem bater exatamente com o CHECK constraint
 * da coluna users.role definido no flowops_ddl.sql.
 * SUPER_ADMIN e CLIENTE fazem parte do dominio documentado (Negocio e Dominio,
 * matriz RBAC) mas estao fora do escopo de implementacao do MVP (Roadmap e Entrega).
 */
public enum Role {
    ADMIN_EMPRESA,
    OPERADOR,
    TECNICO
}
