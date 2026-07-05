package com.flowops.repository;

import com.flowops.entity.WorkOrder;
import com.flowops.enums.WorkOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    Page<WorkOrder> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    List<WorkOrder> findTop5ByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long companyId);

    long countByCompanyIdAndStatusAndDeletedAtIsNull(Long companyId, WorkOrderStatus status);
}
