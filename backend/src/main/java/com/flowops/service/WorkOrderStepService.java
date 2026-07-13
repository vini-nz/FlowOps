package com.flowops.service;

import com.flowops.dto.workorderstep.WorkOrderStepResponse;
import com.flowops.dto.workorderstep.WorkOrderStepStatusUpdateRequest;
import com.flowops.entity.DomainEvent;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.entity.WorkOrderStep;
import com.flowops.enums.StepStatus;
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

        boolean statusChanged = currentStatus != newStatus;

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

    private void recordEvent(WorkOrder workOrder, String eventType, User actor, String payload) {
        DomainEvent event = new DomainEvent();
        event.setWorkOrder(workOrder);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setPayload(payload);
        domainEventRepository.save(event);
    }
}
