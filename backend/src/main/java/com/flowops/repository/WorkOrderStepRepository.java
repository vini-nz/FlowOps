package com.flowops.repository;

import com.flowops.entity.WorkOrderStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderStepRepository extends JpaRepository<WorkOrderStep, Long> {
    List<WorkOrderStep> findByWorkOrderIdOrderByStepOrderAsc(Long workOrderId);
}
