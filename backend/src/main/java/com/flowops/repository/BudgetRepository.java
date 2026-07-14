package com.flowops.repository;

import com.flowops.entity.Budget;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    // workOrder e createdBy sao LAZY - toda leitura usada para montar
    // BudgetResponse carrega os dois na mesma query (padrao do projeto,
    // ver docs/architecture.md), evitando LazyInitializationException.
    @EntityGraph(attributePaths = {"workOrder", "createdBy"})
    Optional<Budget> findByUuidAndCompanyId(UUID uuid, Long companyId);

    @EntityGraph(attributePaths = {"workOrder", "createdBy"})
    Optional<Budget> findByWorkOrderIdAndCompanyId(Long workOrderId, Long companyId);

    boolean existsByWorkOrderId(Long workOrderId);
}
