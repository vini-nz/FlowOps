package com.flowops.service;

import com.flowops.entity.Client;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.entity.WorkOrderStep;
import com.flowops.enums.StepStatus;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.exception.BusinessRuleException;
import com.flowops.repository.ClientRepository;
import com.flowops.repository.UserRepository;
import com.flowops.repository.WorkOrderRepository;
import com.flowops.repository.WorkOrderStepChecklistItemRepository;
import com.flowops.repository.WorkOrderStepRepository;
import com.flowops.repository.WorkflowStepChecklistItemRepository;
import com.flowops.repository.WorkflowStepRepository;
import com.flowops.repository.WorkflowTemplateRepository;
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
 * Travas de status da V2.4 (ADR-0003): o que o usuário pode disparar
 * diretamente e a exigência de execução concluída antes da entrega.
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderServiceStatusGuardTest {

    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private UserRepository userRepository;
    @Mock private DomainEventService domainEventService;
    @Mock private WorkflowTemplateRepository workflowTemplateRepository;
    @Mock private WorkflowStepRepository workflowStepRepository;
    @Mock private WorkOrderStepRepository workOrderStepRepository;
    @Mock private WorkflowStepChecklistItemRepository workflowStepChecklistItemRepository;
    @Mock private WorkOrderStepChecklistItemRepository workOrderStepChecklistItemRepository;

    private WorkOrderService service;

    private static final Long COMPANY_ID = 1L;
    private User actor;
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(
                workOrderRepository, clientRepository, userRepository, domainEventService,
                workflowTemplateRepository, workflowStepRepository, workOrderStepRepository,
                workflowStepChecklistItemRepository, workOrderStepChecklistItemRepository);
        actor = new User();
        actor.setId(9L);
        actor.setName("Operador Demonstração");

        // WorkOrderResponse.from() le client/createdBy - o EntityGraph do
        // repositorio garante isso em producao, aqui montamos a mao.
        Client client = new Client();
        client.setUuid(UUID.randomUUID());
        client.setName("Cliente Demonstração");

        workOrder = new WorkOrder();
        workOrder.setId(10L);
        workOrder.setUuid(UUID.randomUUID());
        workOrder.setClient(client);
        workOrder.setCreatedBy(actor);
    }

    private void givenWorkOrder(WorkOrderStatus status) {
        workOrder.setStatus(status);
        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(workOrder.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(workOrder));
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

    @Test
    void manualTransitionToOrcamentoGeradoIsRejectedWithAnActionableMessage() {
        // O beco sem saida da V2.3: este era o clique que tornava a criacao
        // do orcamento impossivel para sempre.
        givenWorkOrder(WorkOrderStatus.SOLICITACAO_RECEBIDA);

        assertThatThrownBy(() -> service.updateStatus(
                COMPANY_ID, workOrder.getUuid(), WorkOrderStatus.ORCAMENTO_GERADO, actor))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("criar o orçamento");

        verify(workOrderRepository, never()).save(any());
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.SOLICITACAO_RECEBIDA);
    }

    @Test
    void manualTransitionToApprovedIsRejected() {
        givenWorkOrder(WorkOrderStatus.AGUARDANDO_APROVACAO);

        assertThatThrownBy(() -> service.updateStatus(
                COMPANY_ID, workOrder.getUuid(), WorkOrderStatus.APROVADO, actor))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("aprovação ou recusa do orçamento");
    }

    @Test
    void systemCanStillApplyCommercialTransitions() {
        givenWorkOrder(WorkOrderStatus.SOLICITACAO_RECEBIDA);
        when(workOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyDerivedStatus(COMPANY_ID, workOrder.getUuid(), WorkOrderStatus.ORCAMENTO_GERADO, actor);

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.ORCAMENTO_GERADO);
        verify(domainEventService).record(any(), any(), any(), any());
    }

    @Test
    void cannotDeliverWhileAnyStepIsPending() {
        givenWorkOrder(WorkOrderStatus.EM_EXECUCAO);
        when(workOrderStepRepository.findByWorkOrderIdOrderByStepOrderAsc(workOrder.getId()))
                .thenReturn(List.of(
                        step(1, "Produção", StepStatus.CONCLUIDA),
                        step(2, "Acabamento", StepStatus.EM_ANDAMENTO)));

        assertThatThrownBy(() -> service.updateStatus(
                COMPANY_ID, workOrder.getUuid(), WorkOrderStatus.ENTREGUE, actor))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Conclua todas as etapas");

        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void canDeliverWhenEveryStepIsCompleted() {
        givenWorkOrder(WorkOrderStatus.EM_EXECUCAO);
        when(workOrderStepRepository.findByWorkOrderIdOrderByStepOrderAsc(workOrder.getId()))
                .thenReturn(List.of(
                        step(1, "Produção", StepStatus.CONCLUIDA),
                        step(2, "Acabamento", StepStatus.CONCLUIDA)));
        when(workOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(COMPANY_ID, workOrder.getUuid(), WorkOrderStatus.ENTREGUE, actor);

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.ENTREGUE);
    }

    @Test
    void workOrderWithoutStepsCanStillBeDelivered() {
        // Empresa sem workflow_template padrao nasce sem etapas - nao pode
        // ficar impedida de entregar por causa de uma trava criada para
        // WorkOrders que tem execucao.
        givenWorkOrder(WorkOrderStatus.EM_EXECUCAO);
        when(workOrderStepRepository.findByWorkOrderIdOrderByStepOrderAsc(workOrder.getId()))
                .thenReturn(List.of());
        when(workOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(COMPANY_ID, workOrder.getUuid(), WorkOrderStatus.ENTREGUE, actor);

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrderStatus.ENTREGUE);
    }
}
