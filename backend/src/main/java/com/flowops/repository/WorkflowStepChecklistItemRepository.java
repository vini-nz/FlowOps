package com.flowops.repository;

import com.flowops.entity.WorkflowStepChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowStepChecklistItemRepository extends JpaRepository<WorkflowStepChecklistItem, Long> {

    List<WorkflowStepChecklistItem> findByWorkflowStepIdOrderByItemOrderAsc(Long workflowStepId);

    Optional<WorkflowStepChecklistItem> findByUuidAndWorkflowStepId(UUID uuid, Long workflowStepId);

    Optional<WorkflowStepChecklistItem> findFirstByWorkflowStepIdOrderByItemOrderDesc(Long workflowStepId);
}
