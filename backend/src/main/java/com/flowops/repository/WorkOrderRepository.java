package com.flowops.repository;

import com.flowops.entity.WorkOrder;
import com.flowops.enums.WorkOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    // client, assignedTo e createdBy sao LAZY na entidade (padrao do projeto -
    // ver docs/architecture.md). WorkOrderResponse.from() acessa os tres, entao
    // toda consulta usada para montar resposta de API carrega os tres na MESMA
    // query via EntityGraph, evitando N+1 (uma query extra por WorkOrder listada).
    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Page<WorkOrder> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Page<WorkOrder> findByCompanyIdAndStatusAndDeletedAtIsNull(
            Long companyId, WorkOrderStatus status, Pageable pageable);

    List<WorkOrder> findTop5ByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long companyId);

    long countByCompanyIdAndStatusAndDeletedAtIsNull(Long companyId, WorkOrderStatus status);

    // Busca por UUID, nunca pelo id sequencial interno - mesma decisao aplicada
    // a Client (ver docs/architecture.md). Filtro por company_id reforca o
    // isolamento multi-tenant (D-06).
    @EntityGraph(attributePaths = {"client", "assignedTo", "createdBy"})
    Optional<WorkOrder> findByUuidAndCompanyIdAndDeletedAtIsNull(UUID uuid, Long companyId);

    // Usado pelo ClientService para decidir entre exclusao fisica e soft delete.
    // Nao filtra por deleted_at: o FK work_orders.client_id nao diferencia
    // WorkOrder ativa de soft-deleted - mesmo uma WorkOrder ja "excluida"
    // logicamente ainda tem uma linha na tabela apontando para o cliente,
    // e um DELETE fisico do cliente falharia por violacao de FK de qualquer forma.
    boolean existsByClientId(Long clientId);
}


