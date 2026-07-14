package com.flowops.service;

import com.flowops.dto.budget.BudgetItemRequest;
import com.flowops.dto.budget.BudgetItemResponse;
import com.flowops.dto.budget.BudgetResponse;
import com.flowops.entity.Budget;
import com.flowops.entity.BudgetItem;
import com.flowops.entity.CatalogItem;
import com.flowops.entity.Company;
import com.flowops.entity.DomainEvent;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.enums.BudgetStatus;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.exception.BusinessRuleException;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.BudgetItemRepository;
import com.flowops.repository.BudgetRepository;
import com.flowops.repository.CatalogItemRepository;
import com.flowops.repository.DomainEventRepository;
import com.flowops.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Orcamento e catalogo (V2.1 - ver Backlog Detalhado, item 1). Um orcamento
 * por WorkOrder, sem versionamento (ADR-0002). Transicoes de status da
 * WorkOrder sao sempre feitas via WorkOrderService.updateStatus, nunca
 * atribuidas diretamente aqui - reaproveita a validacao da state machine
 * (D-02) e o registro de evento ja existentes, em vez de duplicar.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final BudgetItemRepository budgetItemRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DomainEventRepository domainEventRepository;
    private final WorkOrderService workOrderService;

    @Transactional(readOnly = true)
    public BudgetResponse get(Long companyId, UUID workOrderUuid) {
        WorkOrder workOrder = findWorkOrderOrThrow(companyId, workOrderUuid);
        Budget budget = findBudgetOrThrow(companyId, workOrder.getId());
        return toResponse(budget);
    }

    // Criar orcamento move a WorkOrder de SOLICITACAO_RECEBIDA para
    // ORCAMENTO_GERADO (Fluxo 2 - Comercial, Negocio e Dominio). So permitido
    // nesse estado porque so pode existir 1 orcamento por WorkOrder (ADR-0002).
    @Transactional
    public BudgetResponse create(Long companyId, UUID workOrderUuid, User actor) {
        WorkOrder workOrder = findWorkOrderOrThrow(companyId, workOrderUuid);

        if (workOrder.getStatus() != WorkOrderStatus.SOLICITACAO_RECEBIDA) {
            throw new BusinessRuleException(
                    "Só é possível criar orçamento para uma WorkOrder em SOLICITACAO_RECEBIDA");
        }
        if (budgetRepository.existsByWorkOrderId(workOrder.getId())) {
            throw new BusinessRuleException("Esta WorkOrder já possui um orçamento");
        }

        Budget budget = new Budget();
        budget.setCompany(refCompany(companyId));
        budget.setWorkOrder(workOrder);
        budget.setCreatedBy(actor);
        budgetRepository.save(budget);

        recordEvent(workOrder, "ORCAMENTO_CRIADO", actor, null);
        workOrderService.updateStatus(companyId, workOrderUuid, WorkOrderStatus.ORCAMENTO_GERADO, actor);

        return get(companyId, workOrderUuid);
    }

    @Transactional
    public BudgetResponse addItem(Long companyId, UUID workOrderUuid, BudgetItemRequest request, User actor) {
        WorkOrder workOrder = findWorkOrderOrThrow(companyId, workOrderUuid);
        Budget budget = findBudgetOrThrow(companyId, workOrder.getId());
        assertEditable(budget);

        BudgetItem item = new BudgetItem();
        item.setBudget(budget);

        if (request.catalogItemUuid() != null) {
            CatalogItem catalogItem = catalogItemRepository.findByUuidAndCompanyId(request.catalogItemUuid(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Item de catálogo não encontrado"));
            item.setCatalogItem(catalogItem);
            item.setDescription(StringUtils.hasText(request.description()) ? request.description() : catalogItem.getName());
            item.setUnitPrice(request.unitPrice() != null ? request.unitPrice() : catalogItem.getUnitPrice());
        } else {
            if (!StringUtils.hasText(request.description()) || request.unitPrice() == null) {
                throw new BusinessRuleException(
                        "Informe catalogItemUuid, ou description e unitPrice para um item avulso");
            }
            item.setDescription(request.description());
            item.setUnitPrice(request.unitPrice());
        }

        item.setQuantity(request.quantity());
        item.setSubtotal(item.getUnitPrice().multiply(item.getQuantity()));
        budgetItemRepository.save(item);

        recalculateTotal(budget);
        recordEvent(workOrder, "ITEM_ADICIONADO", actor,
                "{\"description\":\"%s\",\"subtotal\":%s}".formatted(item.getDescription(), item.getSubtotal()));

        return get(companyId, workOrderUuid);
    }

    @Transactional
    public BudgetResponse removeItem(Long companyId, UUID workOrderUuid, UUID itemUuid, User actor) {
        WorkOrder workOrder = findWorkOrderOrThrow(companyId, workOrderUuid);
        Budget budget = findBudgetOrThrow(companyId, workOrder.getId());
        assertEditable(budget);

        BudgetItem item = budgetItemRepository.findByUuidAndBudgetId(itemUuid, budget.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Item de orçamento não encontrado"));

        budgetItemRepository.delete(item);
        recalculateTotal(budget);
        recordEvent(workOrder, "ITEM_REMOVIDO", actor, "{\"description\":\"%s\"}".formatted(item.getDescription()));

        return get(companyId, workOrderUuid);
    }

    // Decisao interna do Operador (V2.1) - aprovacao publica pelo Cliente e
    // escopo do Portal (V3, ver Backlog Detalhado item 2). Encadeia as duas
    // transicoes validas da state machine (ORCAMENTO_GERADO ->
    // AGUARDANDO_APROVACAO -> alvo) reaproveitando WorkOrderService.updateStatus.
    @Transactional
    public BudgetResponse updateStatus(Long companyId, UUID workOrderUuid, BudgetStatus newStatus, User actor) {
        if (newStatus == BudgetStatus.RASCUNHO) {
            throw new BusinessRuleException("Não é possível voltar um orçamento para RASCUNHO");
        }

        WorkOrder workOrder = findWorkOrderOrThrow(companyId, workOrderUuid);
        Budget budget = findBudgetOrThrow(companyId, workOrder.getId());

        if (budget.getStatus() != BudgetStatus.RASCUNHO) {
            throw new BusinessRuleException("Este orçamento já foi decidido");
        }
        if (budget.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Orçamento precisa de ao menos um item antes de ser decidido");
        }

        WorkOrderStatus targetWorkOrderStatus = newStatus == BudgetStatus.APROVADO
                ? WorkOrderStatus.APROVADO
                : WorkOrderStatus.RECUSADO;

        workOrderService.updateStatus(companyId, workOrderUuid, WorkOrderStatus.AGUARDANDO_APROVACAO, actor);
        workOrderService.updateStatus(companyId, workOrderUuid, targetWorkOrderStatus, actor);

        budget.setStatus(newStatus);
        budgetRepository.save(budget);

        recordEvent(workOrder, "ORCAMENTO_" + newStatus, actor, null);

        return get(companyId, workOrderUuid);
    }

    private void assertEditable(Budget budget) {
        if (budget.getStatus() != BudgetStatus.RASCUNHO) {
            throw new BusinessRuleException("Orçamento já decidido não pode mais ser alterado");
        }
    }

    private void recalculateTotal(Budget budget) {
        BigDecimal total = budgetItemRepository.findByBudgetIdOrderByIdAsc(budget.getId()).stream()
                .map(BudgetItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        budget.setTotalAmount(total);
        budgetRepository.save(budget);
    }

    private BudgetResponse toResponse(Budget budget) {
        List<BudgetItemResponse> items = budgetItemRepository.findByBudgetIdOrderByIdAsc(budget.getId()).stream()
                .map(BudgetItemResponse::from)
                .toList();
        return BudgetResponse.from(budget, items);
    }

    private WorkOrder findWorkOrderOrThrow(Long companyId, UUID workOrderUuid) {
        return workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(workOrderUuid, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder não encontrada"));
    }

    private Budget findBudgetOrThrow(Long companyId, Long workOrderId) {
        return budgetRepository.findByWorkOrderIdAndCompanyId(workOrderId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado para esta WorkOrder"));
    }

    private void recordEvent(WorkOrder workOrder, String eventType, User actor, String payload) {
        DomainEvent event = new DomainEvent();
        event.setWorkOrder(workOrder);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setPayload(payload);
        domainEventRepository.save(event);
    }

    private Company refCompany(Long companyId) {
        Company company = new Company();
        company.setId(companyId);
        return company;
    }
}
