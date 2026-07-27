package com.flowops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Metadado de um arquivo anexado a uma etapa (V2.6). O binário nunca passa
 * pelo backend nem pelo banco: fica no storage S3-compatível, endereçado por
 * {@code objectKey}.
 */
@Entity
@Table(name = "evidences")
@Getter
@Setter
@NoArgsConstructor
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_step_id", nullable = false)
    private WorkOrderStep workOrderStep;

    @Column(name = "object_key", nullable = false, length = 500, unique = true)
    private String objectKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** NULL enquanto o upload não foi confirmado — ver comentário no DDL. */
    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
