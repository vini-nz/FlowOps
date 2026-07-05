package com.flowops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tenant logico do sistema (decisao D-06). Todas as tabelas operacionais
 * referenciam company_id para isolamento de dados entre empresas.
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID uuid;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String slug;

    @Column(length = 18, unique = true)
    private String cnpj;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(nullable = false, length = 20)
    private String plan = "FREE";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // created_at/updated_at sao preenchidos pelo DEFAULT now() e pelo
    // trigger set_updated_at() do PostgreSQL (ver flowops_ddl.sql) - o Java
    // nunca escreve nestas colunas, apenas as le de volta.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
