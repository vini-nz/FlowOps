package com.flowops.repository;

import com.flowops.entity.Budget;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    // workOrder, workOrder.client, company, createdBy e decidedBy sao LAZY -
    // toda leitura usada para montar BudgetResponse ou o PDF (V2.2) carrega
    // tudo na mesma query (padrao do projeto, ver docs/architecture.md),
    // evitando LazyInitializationException. decidedBy costuma ser null
    // (orcamento em RASCUNHO) - EntityGraph em associacao null nao gera
    // erro, so nao ha nada para o JOIN trazer.
    @EntityGraph(attributePaths = {"company", "workOrder", "workOrder.client", "createdBy", "decidedBy"})
    Optional<Budget> findByUuidAndCompanyId(UUID uuid, Long companyId);

    @EntityGraph(attributePaths = {"company", "workOrder", "workOrder.client", "createdBy", "decidedBy"})
    Optional<Budget> findByWorkOrderIdAndCompanyId(Long workOrderId, Long companyId);

    boolean existsByWorkOrderId(Long workOrderId);

    // Exportacao (V2.9): busca os orcamentos de um lote de WorkOrders numa
    // query so, para a planilha trazer o valor sem gerar um SELECT por linha.
    List<Budget> findByWorkOrderIdIn(Collection<Long> workOrderIds);
}
