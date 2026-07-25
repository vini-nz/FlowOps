package com.flowops.service;

import com.flowops.dto.workorderstep.WorkOrderStepResponse;
import com.flowops.dto.workorderstep.WorkOrderStepStatusUpdateRequest;
import com.flowops.entity.DomainEvent;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.entity.WorkOrderStep;
import com.flowops.enums.StepStatus;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.exception.BusinessRuleException;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.DomainEventRepository;
import com.flowops.repository.WorkOrderRepository;
import com.flowops.repository.WorkOrderStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkOrderStepService {

    private final WorkOrderStepRepository workOrderStepRepository;
    private final WorkOrderRepository workOrderRepository;
    private final DomainEventRepository domainEventRepository;
    private final WorkOrderService workOrderService;

    @Transactional(readOnly = true)
    public List<WorkOrderStepResponse> list(Long companyId, UUID workOrderUuid) {
        WorkOrder workOrder = findOwnedWorkOrderOrThrow(companyId, workOrderUuid);
        return workOrderStepRepository.findByWorkOrderIdOrderByStepOrderAsc(workOrder.getId())
                .stream()
                .map(WorkOrderStepResponse::from)
                .toList();
    }

    @Transactional
    public WorkOrderStepResponse updateStatus(
            Long companyId, UUID workOrderUuid, UUID stepUuid,
            WorkOrderStepStatusUpdateRequest request, User actor) {
        WorkOrder workOrder = findOwnedWorkOrderOrThrow(companyId, workOrderUuid);

        WorkOrderStep step = workOrderStepRepository.findByUuidAndWorkOrderId(stepUuid, workOrder.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Etapa não encontrada"));

        StepStatus currentStatus = step.getStatus();
        StepStatus newStatus = request.status();

        if (!StepStatusTransitions.isValid(currentStatus, newStatus)) {
            throw new BusinessRuleException(
                    "Transição de etapa inválida: %s → %s".formatted(currentStatus, newStatus));
        }

        // V2.4 (ADR-0003): execucao so existe depois que o comercial fechou.
        // Sem isso, um Tecnico podia concluir a etapa de instalacao com a
        // WorkOrder ainda em SOLICITACAO_RECEBIDA, sem orcamento nenhum.
        assertWorkOrderIsExecutable(workOrder);

        boolean statusChanged = currentStatus != newStatus;

        if (statusChanged && newStatus == StepStatus.EM_ANDAMENTO) {
            assertPreviousStepsCompleted(workOrder, step);
        }

        if (statusChanged && newStatus == StepStatus.EM_ANDAMENTO && step.getStartedAt() == null) {
            step.setStartedAt(OffsetDateTime.now());
        }
        if (statusChanged && newStatus == StepStatus.CONCLUIDA) {
            step.setCompletedAt(OffsetDateTime.now());
        }

        step.setStatus(newStatus);
        if (request.notes() != null) {
            step.setNotes(request.notes());
        }

        WorkOrderStep saved = workOrderStepRepository.save(step);

        if (statusChanged) {
            recordEvent(workOrder, "ETAPA_STATUS_ALTERADA", actor,
                    "{\"etapa\":\"%s\",\"de\":\"%s\",\"para\":\"%s\"}"
                            .formatted(saved.getTitle(), currentStatus, newStatus));

            // Comecar a trabalhar E o fato que define "em execucao" - deixar
            // isso a cargo de um clique manual e o que permitia a WorkOrder
            // ficar em APROVADO com etapas ja em andamento.
            if (newStatus == StepStatus.EM_ANDAMENTO
                    && workOrder.getStatus() == WorkOrderStatus.APROVADO) {
                workOrderService.applyDerivedStatus(
                        companyId, workOrderUuid, WorkOrderStatus.EM_EXECUCAO, actor);
            }
        } else if (request.notes() != null) {
            recordEvent(workOrder, "ETAPA_OBSERVACAO_REGISTRADA", actor,
                    "{\"etapa\":\"%s\"}".formatted(saved.getTitle()));
        }

        return WorkOrderStepResponse.from(saved);
    }

    private WorkOrder findOwnedWorkOrderOrThrow(Long companyId, UUID workOrderUuid) {
        return workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(workOrderUuid, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder não encontrada"));
    }

    private void assertWorkOrderIsExecutable(WorkOrder workOrder) {
        WorkOrderStatus status = workOrder.getStatus();
        if (status != WorkOrderStatus.APROVADO && status != WorkOrderStatus.EM_EXECUCAO) {
            throw new BusinessRuleException(
                    "Etapas só podem ser trabalhadas com o orçamento aprovado e a Ordem de Serviço em execução");
        }
    }

    /**
     * Ordem sequencial simples: uma etapa só começa depois que todas as
     * anteriores (por {@code step_order}) estão concluídas.
     * <p>
     * Deliberadamente <em>não</em> é o grafo de dependências arbitrárias
     * ({@code depends_on_step_id}) previsto em "Etapas Avançadas" na V3 —
     * aquilo resolve casos de etapas paralelas e opcionais, que ainda não
     * existem. Aqui basta impedir o caso real: instalar antes de produzir.
     */
    private void assertPreviousStepsCompleted(WorkOrder workOrder, WorkOrderStep step) {
        workOrderStepRepository.findByWorkOrderIdOrderByStepOrderAsc(workOrder.getId()).stream()
                .filter(other -> other.getStepOrder() < step.getStepOrder())
                .filter(previous -> previous.getStatus() != StepStatus.CONCLUIDA)
                .findFirst()
                .ifPresent(pending -> {
                    throw new BusinessRuleException(
                            "Conclua a etapa \"%s\" antes de iniciar \"%s\""
                                    .formatted(pending.getTitle(), step.getTitle()));
                });
    }

    private void recordEvent(WorkOrder workOrder, String eventType, User actor, String payload) {
        DomainEvent event = new DomainEvent();
        event.setWorkOrder(workOrder);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setPayload(payload);
        domainEventRepository.save(event);
    }
}
