package com.flowops.repository;

import com.flowops.entity.Evidence;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    // Só evidências confirmadas: registros com uploaded_at nulo são uploads
    // iniciados e nunca concluídos, e não representam arquivo nenhum.
    @EntityGraph(attributePaths = "uploadedBy")
    List<Evidence> findByWorkOrderStepIdAndUploadedAtIsNotNullOrderByCreatedAtAsc(Long workOrderStepId);

    @EntityGraph(attributePaths = "uploadedBy")
    Optional<Evidence> findByUuidAndCompanyId(UUID uuid, Long companyId);

    long countByWorkOrderStepIdAndUploadedAtIsNotNull(Long workOrderStepId);
}
