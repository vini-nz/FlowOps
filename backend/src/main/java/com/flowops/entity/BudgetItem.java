package com.flowops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Item de orcamento. description/unitPrice sao gravados como snapshot no
 * momento da criacao - mudancas futuras no CatalogItem nao afetam itens
 * ja adicionados a um orcamento (ver V1__init_schema.sql).
 */
@Entity
@Table(name = "budget_items")
@Getter
@Setter
@NoArgsConstructor
public class BudgetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_item_id")
    private CatalogItem catalogItem;

    @Column(nullable = false, length = 150)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
