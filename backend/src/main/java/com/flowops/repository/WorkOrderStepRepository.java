package com.flowops.repository;

import com.flowops.entity.WorkOrderStep;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderStepRepository extends JpaRepository<WorkOrderStep, Long> {

    @EntityGraph(attributePaths = "assignedTo")
    List<WorkOrderStep> findByWorkOrderIdOrderByStepOrderAsc(Long workOrderId);

    // Escopado por work_order_id (nao so pelo uuid da etapa): reforca que uma
    // etapa so pode ser alterada atraves da WorkOrder a que pertence, que por
    // sua vez ja foi resolvida com isolamento por company_id no Controller/
    // Service (mesmo padrao de Client e WorkOrder - ver docs/architecture.md).
    @EntityGraph(attributePaths = "assignedTo")
    Optional<WorkOrderStep> findByUuidAndWorkOrderId(UUID uuid, Long workOrderId);
}
