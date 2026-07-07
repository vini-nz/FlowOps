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

    // Usado pelo ClientService para decidir entre exclusao fisica e soft delete.
    // Nao filtra por deleted_at: o FK work_orders.client_id nao diferencia
    // WorkOrder ativa de soft-deleted - mesmo uma WorkOrder ja "excluida"
    // logicamente ainda tem uma linha na tabela apontando para o cliente,
    // e um DELETE fisico do cliente falharia por violacao de FK de qualquer forma.
    boolean existsByClientId(Long clientId);
}
