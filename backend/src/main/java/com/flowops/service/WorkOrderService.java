package com.flowops.service;

import com.flowops.dto.workorder.WorkOrderRequest;
import com.flowops.dto.workorder.WorkOrderResponse;
import com.flowops.entity.Client;
import com.flowops.entity.Company;
import com.flowops.entity.DomainEvent;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.entity.WorkOrderStep;
import com.flowops.entity.WorkflowTemplate;
import com.flowops.enums.Priority;
import com.flowops.enums.StepStatus;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.exception.BusinessRuleException;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.ClientRepository;
import com.flowops.repository.DomainEventRepository;
import com.flowops.repository.UserRepository;
import com.flowops.repository.WorkOrderRepository;
import com.flowops.repository.WorkOrderStepRepository;
import com.flowops.repository.WorkflowStepRepository;
import com.flowops.repository.WorkflowTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final DomainEventRepository domainEventRepository;
    private final WorkflowTemplateRepository workflowTemplateRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final WorkOrderStepRepository workOrderStepRepository;

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> list(Long companyId, WorkOrderStatus status, Pageable pageable) {
        Page<WorkOrder> page = status != null
                ? workOrderRepository.findByCompanyIdAndStatusAndDeletedAtIsNull(companyId, status, pageable)
                : workOrderRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable);

        return page.map(WorkOrderResponse::from);
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse get(Long companyId, UUID workOrderUuid) {
        return WorkOrderResponse.from(findOwnedOrThrow(companyId, workOrderUuid));
    }

    @Transactional
    public WorkOrderResponse create(Long companyId, User createdBy, WorkOrderRequest request) {
        Client client = clientRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(request.clientUuid(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado"));

        WorkOrder workOrder = new WorkOrder();
        workOrder.setCompany(refCompany(companyId));
        workOrder.setClient(client);
        workOrder.setTitle(request.title());
        workOrder.setDescription(request.description());
        workOrder.setPriority(request.priority() != null ? request.priority() : Priority.NORMAL);
        workOrder.setScheduledStart(request.scheduledStart());
        workOrder.setScheduledEnd(request.scheduledEnd());
        workOrder.setCreatedBy(createdBy);

        if (request.assignedToUuid() != null) {
            workOrder.setAssignedTo(resolveAssignee(companyId, request.assignedToUuid()));
        }

        // Workflow configuravel por empresa (Fluxo 3 - Planejamento, Negocio e
        // Dominio no Notion): se a empresa tem um workflow_template default,
        // a WorkOrder nasce ja vinculada a ele e suas etapas sao instanciadas
        // a partir do molde (workflow_steps -> work_order_steps). Sem template
        // default, a WorkOrder simplesmente nasce sem etapas - nao e um erro.
        workflowTemplateRepository.findByCompanyIdAndIsDefaultTrue(companyId)
                .ifPresent(workOrder::setWorkflowTemplate);

        WorkOrder saved = workOrderRepository.save(workOrder);
        recordEvent(saved, "WORKORDER_CRIADA", createdBy, null);

        if (saved.getWorkflowTemplate() != null) {
            instantiateSteps(saved);
        }

        // Recarrega com o EntityGraph para que a resposta tenha client/assignedTo/
        // createdBy ja carregados, coerente com o resto do modulo.
        return get(companyId, saved.getUuid());
    }

    private void instantiateSteps(WorkOrder workOrder) {
        workflowStepRepository.findByWorkflowTemplateIdOrderByStepOrderAsc(workOrder.getWorkflowTemplate().getId())
                .forEach(workflowStep -> {
                    WorkOrderStep step = new WorkOrderStep();
                    step.setWorkOrder(workOrder);
                    step.setWorkflowStep(workflowStep);
                    step.setStepOrder(workflowStep.getStepOrder());
                    step.setTitle(workflowStep.getTitle());
                    workOrderStepRepository.save(step);
                });
    }

    /**
     * Transição disparada diretamente pelo usuário (Controller). Só aceita o
     * subconjunto manual da state machine: a fase comercial
     * (ORCAMENTO_GERADO/AGUARDANDO_APROVACAO/APROVADO/RECUSADO) é
     * consequência de ações no orçamento e chega por
     * {@link #applyDerivedStatus}. Ver ADR-0003.
     */
    @Transactional
    public WorkOrderResponse updateStatus(Long companyId, UUID workOrderUuid, WorkOrderStatus newStatus, User actor) {
        WorkOrder workOrder = findOwnedOrThrow(companyId, workOrderUuid);
        WorkOrderStatus currentStatus = workOrder.getStatus();

        if (!WorkOrderStatusTransitions.isManual(currentStatus, newStatus)) {
            throw new BusinessRuleException(explainRejectedManualTransition(currentStatus, newStatus));
        }
        // Uma WorkOrder nao pode ser entregue enquanto sobra trabalho a fazer:
        // sem isso o status podia caminhar ate FINALIZADO com todas as etapas
        // ainda em PENDENTE, e o sistema afirmava uma entrega que nunca houve.
        if (newStatus == WorkOrderStatus.ENTREGUE) {
            assertAllStepsCompleted(workOrder);
        }

        return transitionTo(workOrder, newStatus, actor);
    }

    /**
     * Transição aplicada pelo próprio sistema como consequência de um fato
     * registrado em outro módulo — orçamento criado/decidido
     * ({@code BudgetService}) ou primeira etapa iniciada
     * ({@code WorkOrderStepService}). Valida contra a máquina completa, não
     * contra o subconjunto manual.
     */
    @Transactional
    public WorkOrderResponse applyDerivedStatus(
            Long companyId, UUID workOrderUuid, WorkOrderStatus newStatus, User actor) {
        WorkOrder workOrder = findOwnedOrThrow(companyId, workOrderUuid);
        WorkOrderStatus currentStatus = workOrder.getStatus();

        if (!WorkOrderStatusTransitions.isValid(currentStatus, newStatus)) {
            throw new BusinessRuleException(
                    "Transição inválida: %s → %s".formatted(currentStatus, newStatus));
        }

        return transitionTo(workOrder, newStatus, actor);
    }

    private WorkOrderResponse transitionTo(WorkOrder workOrder, WorkOrderStatus newStatus, User actor) {
        WorkOrderStatus currentStatus = workOrder.getStatus();

        workOrder.setStatus(newStatus);
        WorkOrder saved = workOrderRepository.save(workOrder);

        recordEvent(saved, "STATUS_ALTERADO", actor,
                "{\"de\":\"%s\",\"para\":\"%s\"}".formatted(currentStatus, newStatus));

        return WorkOrderResponse.from(saved);
    }

    private void assertAllStepsCompleted(WorkOrder workOrder) {
        // WorkOrder sem workflow padrao nasce sem etapas - continua podendo
        // ser entregue, senao empresas sem template ficariam travadas.
        boolean hasPendingStep = workOrderStepRepository
                .findByWorkOrderIdOrderByStepOrderAsc(workOrder.getId()).stream()
                .anyMatch(step -> step.getStatus() != StepStatus.CONCLUIDA);

        if (hasPendingStep) {
            throw new BusinessRuleException(
                    "Conclua todas as etapas antes de marcar a Ordem de Serviço como entregue");
        }
    }

    // Mensagem explicando o que fazer, em vez de um "transicao invalida" seco:
    // a fase comercial deixou de ser manual na V2.4 e o usuario precisa saber
    // qual acao provoca aquele status.
    private String explainRejectedManualTransition(WorkOrderStatus from, WorkOrderStatus to) {
        if (WorkOrderStatusTransitions.isValid(from, to)) {
            return switch (to) {
                case ORCAMENTO_GERADO ->
                        "O status muda sozinho ao criar o orçamento desta Ordem de Serviço";
                case AGUARDANDO_APROVACAO, APROVADO, RECUSADO ->
                        "O status muda sozinho ao registrar a aprovação ou recusa do orçamento";
                default -> "Transição inválida: %s → %s".formatted(from, to);
            };
        }
        return "Transição inválida: %s → %s".formatted(from, to);
    }

    @Transactional
    public WorkOrderResponse assign(Long companyId, UUID workOrderUuid, UUID assignedToUuid, User actor) {
        WorkOrder workOrder = findOwnedOrThrow(companyId, workOrderUuid);

        User assignee = assignedToUuid != null ? resolveAssignee(companyId, assignedToUuid) : null;
        workOrder.setAssignedTo(assignee);

        WorkOrder saved = workOrderRepository.save(workOrder);

        recordEvent(saved, "RESPONSAVEL_ATRIBUIDO", actor,
                assignee != null ? "{\"assignedTo\":\"%s\"}".formatted(assignee.getName()) : "{\"assignedTo\":null}");

        return WorkOrderResponse.from(saved);
    }

    private WorkOrder findOwnedOrThrow(Long companyId, UUID workOrderUuid) {
        return workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(workOrderUuid, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder não encontrada"));
    }

    private User resolveAssignee(Long companyId, UUID userUuid) {
        return userRepository.findByUuidAndCompanyIdAndActiveTrue(userUuid, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário responsável não encontrado"));
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
