package com.flowops.service;

import com.flowops.dto.workorderstep.WorkOrderStepStatusUpdateRequest;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.entity.WorkOrderStep;
import com.flowops.enums.StepStatus;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.exception.BusinessRuleException;
import com.flowops.repository.DomainEventRepository;
import com.flowops.repository.WorkOrderRepository;
import com.flowops.repository.WorkOrderStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Travas de integridade da execução introduzidas na V2.4 (ADR-0003). Até a
 * V2.3 era possível concluir a etapa de instalação numa WorkOrder ainda em
 * SOLICITACAO_RECEBIDA, sem orçamento nenhum, pulando as etapas anteriores.
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderStepServiceTest {

    @Mock
    private WorkOrderStepRepository workOrderStepRepository;
    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private DomainEventRepository domainEventRepository;
    @Mock
    private WorkOrderService workOrderService;

    private WorkOrderStepService service;

    private static final Long COMPANY_ID = 1L;
    private User actor;
    private WorkOrder workOrder;
    private WorkOrderStep producao;
    private WorkOrderStep acabamento;
    private WorkOrderStep instalacao;

    @BeforeEach
    void setUp() {
        service = new WorkOrderStepService(
                workOrderStepRepository, workOrderRepository, domainEventRepository, workOrderService);
        actor = new User();
        actor.setId(9L);

        workOrder = new WorkOrder();
        workOrder.setId(10L);
        workOrder.setUuid(UUID.randomUUID());

        producao = step(1, "Produção", StepStatus.PENDENTE);
        acabamento = step(2, "Acabamento", StepStatus.PENDENTE);
        instalacao = step(3, "Instalação", StepStatus.PENDENTE);
    }

    private WorkOrderStep step(int order, String title, StepStatus status) {
        WorkOrderStep step = new WorkOrderStep();
        step.setUuid(UUID.randomUUID());
        step.setWorkOrder(workOrder);
        step.setStepOrder(order);
        step.setTitle(title);
        step.setStatus(status);
        return step;
    }

    private void givenWorkOrder(WorkOrderStatus status) {
        workOrder.setStatus(status);
        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(workOrder.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(workOrder));
    }

    private void givenStepLookup(WorkOrderStep step) {
        when(workOrderStepRepository.findByUuidAndWorkOrderId(step.getUuid(), workOrder.getId()))
                .thenReturn(Optional.of(step));
    }

    private WorkOrderStepStatusUpdateRequest request(StepStatus status) {
        return new WorkOrderStepStatusUpdateRequest(status, null);
    }

    @Test
    void cannotWorkStepsBeforeBudgetApproval() {
        givenWorkOrder(WorkOrderStatus.SOLICITACAO_RECEBIDA);
        givenStepLookup(producao);

        assertThatThrownBy(() -> service.updateStatus(
                COMPANY_ID, workOrder.getUuid(), producao.getUuid(), request(StepStatus.EM_ANDAMENTO), actor))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("orçamento aprovado");

        verify(workOrderStepRepository, never()).save(any());
    }

    @Test
    void cannotWorkStepsAfterDelivery() {
        givenWorkOrder(WorkOrderStatus.ENTREGUE);
        givenStepLookup(producao);

        assertThatThrownBy(() -> service.updateStatus(
                COMPANY_ID, workOrder.getUuid(), producao.getUuid(), request(StepStatus.EM_ANDAMENTO), actor))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cannotStartStepWhilePreviousIsNotCompleted() {
        givenWorkOrder(WorkOrderStatus.EM_EXECUCAO);
        givenStepLookup(instalacao);
        when(workOrderStepRepository.findByWorkOrderIdOrderByStepOrderAsc(workOrder.getId()))
                .thenReturn(List.of(producao, acabamento, instalacao));

        assertThatThrownBy(() -> service.updateStatus(
                COMPANY_ID, workOrder.getUuid(), instalacao.getUuid(), request(StepStatus.EM_ANDAMENTO), actor))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Produção");

        verify(workOrderStepRepository, never()).save(any());
    }

    @Test
    void canStartStepWhenAllPreviousAreCompleted() {
        givenWorkOrder(WorkOrderStatus.EM_EXECUCAO);
        producao.setStatus(StepStatus.CONCLUIDA);
        givenStepLookup(acabamento);
        when(workOrderStepRepository.findByWorkOrderIdOrderByStepOrderAsc(workOrder.getId()))
                .thenReturn(List.of(producao, acabamento, instalacao));
        when(workOrderStepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateStatus(
                COMPANY_ID, workOrder.getUuid(), acabamento.getUuid(), request(StepStatus.EM_ANDAMENTO), actor);

        assertThat(response.status()).isEqualTo(StepStatus.EM_ANDAMENTO);
    }

    @Test
    void startingFirstStepMovesApprovedWorkOrderToInExecution() {
        givenWorkOrder(WorkOrderStatus.APROVADO);
        givenStepLookup(producao);
        when(workOrderStepRepository.findByWorkOrderIdOrderByStepOrderAsc(workOrder.getId()))
                .thenReturn(List.of(producao, acabamento, instalacao));
        when(workOrderStepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(
                COMPANY_ID, workOrder.getUuid(), producao.getUuid(), request(StepStatus.EM_ANDAMENTO), actor);

        verify(workOrderService).applyDerivedStatus(
                COMPANY_ID, workOrder.getUuid(), WorkOrderStatus.EM_EXECUCAO, actor);
    }

    @Test
    void startingStepWhenAlreadyInExecutionDoesNotTransitionAgain() {
        givenWorkOrder(WorkOrderStatus.EM_EXECUCAO);
        givenStepLookup(producao);
        when(workOrderStepRepository.findByWorkOrderIdOrderByStepOrderAsc(workOrder.getId()))
                .thenReturn(List.of(producao, acabamento, instalacao));
        when(workOrderStepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(
                COMPANY_ID, workOrder.getUuid(), producao.getUuid(), request(StepStatus.EM_ANDAMENTO), actor);

        verify(workOrderService, never()).applyDerivedStatus(any(), any(), any(), any());
    }

    @Test
    void blockingAFutureStepDoesNotRequirePreviousToBeCompleted() {
        // Bloquear nao e comecar: so a entrada em EM_ANDAMENTO depende da
        // etapa anterior, senao seria impossivel sinalizar impedimento numa
        // etapa futura (ex: material em falta).
        givenWorkOrder(WorkOrderStatus.EM_EXECUCAO);
        givenStepLookup(instalacao);
        when(workOrderStepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateStatus(
                COMPANY_ID, workOrder.getUuid(), instalacao.getUuid(), request(StepStatus.BLOQUEADA), actor);

        assertThat(response.status()).isEqualTo(StepStatus.BLOQUEADA);
    }
}
