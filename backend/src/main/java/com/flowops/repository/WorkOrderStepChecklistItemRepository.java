package com.flowops.repository;

import com.flowops.entity.WorkOrderStepChecklistItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderStepChecklistItemRepository extends JpaRepository<WorkOrderStepChecklistItem, Long> {

    // doneBy e LAZY e a resposta mostra quem marcou o item - EntityGraph pelo
    // mesmo motivo documentado em WorkOrderRepository (evita N+1 e
    // LazyInitializationException fora da transacao do Service).
    @EntityGraph(attributePaths = "doneBy")
    List<WorkOrderStepChecklistItem> findByWorkOrderStepIdOrderByItemOrderAsc(Long workOrderStepId);

    @EntityGraph(attributePaths = "doneBy")
    Optional<WorkOrderStepChecklistItem> findByUuidAndWorkOrderStepId(UUID uuid, Long workOrderStepId);

    Optional<WorkOrderStepChecklistItem> findFirstByWorkOrderStepIdOrderByItemOrderDesc(Long workOrderStepId);

    long countByWorkOrderStepIdAndDoneFalse(Long workOrderStepId);
}
