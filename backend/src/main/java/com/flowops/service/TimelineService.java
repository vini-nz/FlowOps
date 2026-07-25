package com.flowops.service;

import com.flowops.dto.timeline.TimelineEventResponse;
import com.flowops.entity.WorkOrder;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.DomainEventRepository;
import com.flowops.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Timeline de uma WorkOrder (V2.3). Somente leitura: os eventos ja sao
 * gravados desde a Sprint 3 por WorkOrderService/WorkOrderStepService/
 * BudgetService - aqui nada e produzido, so lido e traduzido.
 */
@Service
@RequiredArgsConstructor
public class TimelineService {

    private final DomainEventRepository domainEventRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TimelineDescriptionFormatter descriptionFormatter;

    @Transactional(readOnly = true)
    public List<TimelineEventResponse> list(Long companyId, UUID workOrderUuid) {
        WorkOrder workOrder = workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(workOrderUuid, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder não encontrada"));

        return domainEventRepository.findByWorkOrderIdOrderByOccurredAtAscIdAsc(workOrder.getId()).stream()
                .map(event -> new TimelineEventResponse(
                        event.getEventType(),
                        descriptionFormatter.describe(event.getEventType(), event.getPayload()),
                        event.getActor() != null ? event.getActor().getName() : null,
                        event.getOccurredAt()))
                .toList();
    }
}
