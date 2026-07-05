package com.flowops.repository;

import com.flowops.entity.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, Long> {
    List<WorkflowStep> findByWorkflowTemplateIdOrderByStepOrderAsc(Long workflowTemplateId);
}
