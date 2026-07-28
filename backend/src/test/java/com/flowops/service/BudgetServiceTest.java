package com.flowops.service;

import com.flowops.dto.budget.BudgetItemRequest;
import com.flowops.entity.Budget;
import com.flowops.entity.BudgetItem;
import com.flowops.entity.CatalogItem;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.enums.BudgetStatus;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.exception.BusinessRuleException;
import com.flowops.repository.BudgetItemRepository;
import com.flowops.repository.BudgetRepository;
import com.flowops.repository.CatalogItemRepository;
import com.flowops.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre as regras de negocio proprias do BudgetService (V2.1) - nao
 * reexercita WorkOrderStatusTransitions, ja coberta por
 * WorkOrderService/testes anteriores; aqui o foco e o que e novo:
 * "1 orcamento por WorkOrder" (ADR-0002), edicao restrita a RASCUNHO,
 * calculo de subtotal/total e a exigencia de ao menos 1 item para decidir.
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;
    @Mock
    private BudgetItemRepository budgetItemRepository;
    @Mock
    private CatalogItemRepository catalogItemRepository;
    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private DomainEventService domainEventService;
    @Mock
    private WorkOrderService workOrderService;
    @Mock
    private BudgetPdfService budgetPdfService;

    private BudgetService budgetService;

    private static final Long COMPANY_ID = 1L;
    private User actor;

    @BeforeEach
    void setUp() {
        budgetService = new BudgetService(
                budgetRepository, budgetItemRepository, catalogItemRepository,
                workOrderRepository, domainEventService, workOrderService, budgetPdfService);
        actor = new User();
        actor.setId(9L);
    }

    private WorkOrder workOrder(WorkOrderStatus status) {
        WorkOrder wo = new WorkOrder();
        wo.setId(10L);
        wo.setUuid(UUID.randomUUID());
        wo.setStatus(status);
        return wo;
    }

    private Budget budget(WorkOrder wo, BudgetStatus status, BigDecimal total) {
        Budget b = new Budget();
        b.setId(20L);
        b.setUuid(UUID.randomUUID());
        b.setWorkOrder(wo);
        b.setStatus(status);
        b.setTotalAmount(total);
        b.setCreatedBy(actor);
        return b;
    }

    @Test
    void create_whenWorkOrderInSolicitacaoRecebida_createsBudgetAndAdvancesWorkOrder() {
        WorkOrder wo = workOrder(WorkOrderStatus.SOLICITACAO_RECEBIDA);
        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(budgetRepository.existsByWorkOrderId(wo.getId())).thenReturn(false);
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));
        when(budgetRepository.findByWorkOrderIdAndCompanyId(wo.getId(), COMPANY_ID))
                .thenReturn(Optional.of(budget(wo, BudgetStatus.RASCUNHO, BigDecimal.ZERO)));
        when(budgetItemRepository.findByBudgetIdOrderByIdAsc(anyLong())).thenReturn(List.of());

        var response = budgetService.create(COMPANY_ID, wo.getUuid(), actor);

        assertThat(response.status()).isEqualTo(BudgetStatus.RASCUNHO);
        verify(workOrderService).applyDerivedStatus(COMPANY_ID, wo.getUuid(), WorkOrderStatus.ORCAMENTO_GERADO, actor);
        verify(domainEventService).record(any(), any(), any(), any());
    }

    @Test
    void create_whenWorkOrderNotInSolicitacaoRecebida_throwsBusinessRuleException() {
        WorkOrder wo = workOrder(WorkOrderStatus.EM_EXECUCAO);
        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));

        assertThatThrownBy(() -> budgetService.create(COMPANY_ID, wo.getUuid(), actor))
                .isInstanceOf(BusinessRuleException.class);

        verify(budgetRepository, never()).save(any());
        verify(workOrderService, never()).applyDerivedStatus(any(), any(), any(), any());
    }

    @Test
    void create_whenWorkOrderAlreadyHasBudget_throwsBusinessRuleException() {
        WorkOrder wo = workOrder(WorkOrderStatus.SOLICITACAO_RECEBIDA);
        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(budgetRepository.existsByWorkOrderId(wo.getId())).thenReturn(true);

        assertThatThrownBy(() -> budgetService.create(COMPANY_ID, wo.getUuid(), actor))
                .isInstanceOf(BusinessRuleException.class);

        verify(budgetRepository, never()).save(any());
    }

    @Test
    void addItem_withCatalogItem_snapshotsNameAndPriceAndRecalculatesTotal() {
        WorkOrder wo = workOrder(WorkOrderStatus.ORCAMENTO_GERADO);
        Budget b = budget(wo, BudgetStatus.RASCUNHO, BigDecimal.ZERO);
        CatalogItem catalogItem = new CatalogItem();
        catalogItem.setId(30L);
        catalogItem.setUuid(UUID.randomUUID());
        catalogItem.setName("Hora tecnica");
        catalogItem.setUnitPrice(new BigDecimal("150.00"));

        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(budgetRepository.findByWorkOrderIdAndCompanyId(wo.getId(), COMPANY_ID)).thenReturn(Optional.of(b));
        when(catalogItemRepository.findByUuidAndCompanyId(catalogItem.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(catalogItem));
        // O item ja esta persistido (budgetItemRepository.save aconteceu antes)
        // quando recalculateTotal soma os subtotais - a mesma lista serve para
        // o recalculo do total e para a resposta.
        when(budgetItemRepository.findByBudgetIdOrderByIdAsc(b.getId()))
                .thenReturn(List.of(itemWithSubtotal(new BigDecimal("300.00"))));

        var request = new BudgetItemRequest(catalogItem.getUuid(), null, new BigDecimal("2"), null);
        var response = budgetService.addItem(COMPANY_ID, wo.getUuid(), request, actor);

        assertThat(response.totalAmount()).isEqualByComparingTo("300.00");
        assertThat(b.getTotalAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void addItem_whenBudgetNotRascunho_throwsBusinessRuleException() {
        WorkOrder wo = workOrder(WorkOrderStatus.APROVADO);
        Budget b = budget(wo, BudgetStatus.APROVADO, new BigDecimal("300.00"));

        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(budgetRepository.findByWorkOrderIdAndCompanyId(wo.getId(), COMPANY_ID)).thenReturn(Optional.of(b));

        var request = new BudgetItemRequest(null, "Item avulso", BigDecimal.ONE, BigDecimal.TEN);

        assertThatThrownBy(() -> budgetService.addItem(COMPANY_ID, wo.getUuid(), request, actor))
                .isInstanceOf(BusinessRuleException.class);

        verify(budgetItemRepository, never()).save(any());
    }

    @Test
    void addItem_avulsoWithoutDescriptionOrPrice_throwsBusinessRuleException() {
        WorkOrder wo = workOrder(WorkOrderStatus.ORCAMENTO_GERADO);
        Budget b = budget(wo, BudgetStatus.RASCUNHO, BigDecimal.ZERO);

        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(budgetRepository.findByWorkOrderIdAndCompanyId(wo.getId(), COMPANY_ID)).thenReturn(Optional.of(b));

        var request = new BudgetItemRequest(null, null, BigDecimal.ONE, null);

        assertThatThrownBy(() -> budgetService.addItem(COMPANY_ID, wo.getUuid(), request, actor))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void updateStatus_whenTotalIsZero_throwsBusinessRuleException() {
        WorkOrder wo = workOrder(WorkOrderStatus.ORCAMENTO_GERADO);
        Budget b = budget(wo, BudgetStatus.RASCUNHO, BigDecimal.ZERO);

        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(budgetRepository.findByWorkOrderIdAndCompanyId(wo.getId(), COMPANY_ID)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> budgetService.updateStatus(COMPANY_ID, wo.getUuid(), BudgetStatus.APROVADO, actor))
                .isInstanceOf(BusinessRuleException.class);

        verify(workOrderService, never()).applyDerivedStatus(any(), any(), any(), any());
    }

    @Test
    void updateStatus_approved_chainsWorkOrderTransitionsAndSetsBudgetApproved() {
        WorkOrder wo = workOrder(WorkOrderStatus.ORCAMENTO_GERADO);
        Budget b = budget(wo, BudgetStatus.RASCUNHO, new BigDecimal("300.00"));

        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(budgetRepository.findByWorkOrderIdAndCompanyId(wo.getId(), COMPANY_ID)).thenReturn(Optional.of(b));
        when(budgetItemRepository.findByBudgetIdOrderByIdAsc(b.getId())).thenReturn(List.of());

        budgetService.updateStatus(COMPANY_ID, wo.getUuid(), BudgetStatus.APROVADO, actor);

        verify(workOrderService).applyDerivedStatus(COMPANY_ID, wo.getUuid(), WorkOrderStatus.AGUARDANDO_APROVACAO, actor);
        verify(workOrderService).applyDerivedStatus(COMPANY_ID, wo.getUuid(), WorkOrderStatus.APROVADO, actor);
        verify(workOrderService, times(2)).applyDerivedStatus(eq(COMPANY_ID), eq(wo.getUuid()), any(), eq(actor));
        assertThat(b.getStatus()).isEqualTo(BudgetStatus.APROVADO);
        // Criterio de aceitacao V2.2: "registrar aprovacao/recusa com data/hora e responsavel"
        assertThat(b.getDecidedBy()).isEqualTo(actor);
        assertThat(b.getDecidedAt()).isNotNull();
    }

    @Test
    void updateStatus_whenAlreadyDecided_throwsBusinessRuleException() {
        WorkOrder wo = workOrder(WorkOrderStatus.APROVADO);
        Budget b = budget(wo, BudgetStatus.APROVADO, new BigDecimal("300.00"));

        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(budgetRepository.findByWorkOrderIdAndCompanyId(wo.getId(), COMPANY_ID)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> budgetService.updateStatus(COMPANY_ID, wo.getUuid(), BudgetStatus.RECUSADO, actor))
                .isInstanceOf(BusinessRuleException.class);

        verify(workOrderService, never()).applyDerivedStatus(any(), any(), any(), any());
    }

    @Test
    void generatePdf_delegatesToBudgetPdfServiceWithBudgetAndItems() {
        WorkOrder wo = workOrder(WorkOrderStatus.APROVADO);
        Budget b = budget(wo, BudgetStatus.APROVADO, new BigDecimal("300.00"));
        List<BudgetItem> items = List.of(itemWithSubtotal(new BigDecimal("300.00")));
        byte[] expectedPdf = {1, 2, 3};

        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(budgetRepository.findByWorkOrderIdAndCompanyId(wo.getId(), COMPANY_ID)).thenReturn(Optional.of(b));
        when(budgetItemRepository.findByBudgetIdOrderByIdAsc(b.getId())).thenReturn(items);
        when(budgetPdfService.generate(b, items)).thenReturn(expectedPdf);

        byte[] result = budgetService.generatePdf(COMPANY_ID, wo.getUuid());

        assertThat(result).isEqualTo(expectedPdf);
    }

    @Test
    void generatePdf_whenBudgetBelongsToAnotherCompany_throwsResourceNotFound() {
        UUID workOrderUuid = UUID.randomUUID();
        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(workOrderUuid, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.generatePdf(COMPANY_ID, workOrderUuid))
                .isInstanceOf(com.flowops.exception.ResourceNotFoundException.class);
    }

    private BudgetItem itemWithSubtotal(BigDecimal subtotal) {
        BudgetItem item = new BudgetItem();
        item.setUuid(UUID.randomUUID());
        item.setDescription("Hora tecnica");
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("150.00"));
        item.setSubtotal(subtotal);
        return item;
    }
}
