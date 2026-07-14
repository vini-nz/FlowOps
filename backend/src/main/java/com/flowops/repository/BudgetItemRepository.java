package com.flowops.repository;

import com.flowops.entity.BudgetItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetItemRepository extends JpaRepository<BudgetItem, Long> {

    @EntityGraph(attributePaths = "catalogItem")
    List<BudgetItem> findByBudgetIdOrderByIdAsc(Long budgetId);

    Optional<BudgetItem> findByUuidAndBudgetId(UUID uuid, Long budgetId);
}
