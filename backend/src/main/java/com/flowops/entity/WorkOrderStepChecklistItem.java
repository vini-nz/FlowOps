package com.flowops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Item de checklist de uma etapa concreta (V2.5). Nasce como cópia do molde
 * ({@code workflowChecklistItem} preenchido) ou avulso, criado pelo Técnico
 * durante a execução daquela OS ({@code workflowChecklistItem} nulo).
 * <p>
 * {@code description} é sempre cópia própria, nunca lida do molde: renomear
 * um item no molde não pode reescrever o que um Técnico já marcou.
 */
@Entity
@Table(name = "work_order_step_checklist_items")
@Getter
@Setter
@NoArgsConstructor
public class WorkOrderStepChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_step_id", nullable = false)
    private WorkOrderStep workOrderStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_checklist_item_id")
    private WorkflowStepChecklistItem workflowChecklistItem;

    @Column(name = "item_order", nullable = false)
    private Integer itemOrder;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "is_done", nullable = false)
    private boolean done = false;

    @Column(name = "done_at")
    private OffsetDateTime doneAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "done_by_id")
    private User doneBy;

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
