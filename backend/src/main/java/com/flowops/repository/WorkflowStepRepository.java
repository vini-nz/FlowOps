package com.flowops.repository;

import com.flowops.entity.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, Long> {

    List<WorkflowStep> findByWorkflowTemplateIdOrderByStepOrderAsc(Long workflowTemplateId);

    // Escopado pelo template (que por sua vez ja foi resolvido com filtro de
    // company_id no Service): garante que uma etapa so e alterada atraves do
    // molde a que pertence.
    Optional<WorkflowStep> findByUuidAndWorkflowTemplateId(UUID uuid, Long workflowTemplateId);

    // Proxima posicao livre ao acrescentar uma etapa ao fim do molde.
    Optional<WorkflowStep> findFirstByWorkflowTemplateIdOrderByStepOrderDesc(Long workflowTemplateId);
}
