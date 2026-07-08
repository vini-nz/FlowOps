package com.flowops.service;

import com.flowops.dto.workorder.WorkOrderRequest;
import com.flowops.dto.workorder.WorkOrderResponse;
import com.flowops.entity.Client;
import com.flowops.entity.Company;
import com.flowops.entity.DomainEvent;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.enums.Priority;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.exception.BusinessRuleException;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.ClientRepository;
import com.flowops.repository.DomainEventRepository;
import com.flowops.repository.UserRepository;
import com.flowops.repository.WorkOrderRepository;
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

        WorkOrder saved = workOrderRepository.save(workOrder);
        recordEvent(saved, "WORKORDER_CRIADA", createdBy, null);

        // Recarrega com o EntityGraph para que a resposta tenha client/assignedTo/
        // createdBy ja carregados, coerente com o resto do modulo.
        return get(companyId, saved.getUuid());
    }

    @Transactional
    public WorkOrderResponse updateStatus(Long companyId, UUID workOrderUuid, WorkOrderStatus newStatus, User actor) {
        WorkOrder workOrder = findOwnedOrThrow(companyId, workOrderUuid);
        WorkOrderStatus currentStatus = workOrder.getStatus();

        if (!WorkOrderStatusTransitions.isValid(currentStatus, newStatus)) {
            throw new BusinessRuleException(
                    "Transição inválida: %s → %s".formatted(currentStatus, newStatus));
        }

        workOrder.setStatus(newStatus);
        WorkOrder saved = workOrderRepository.save(workOrder);

        recordEvent(saved, "STATUS_ALTERADO", actor,
                "{\"de\":\"%s\",\"para\":\"%s\"}".formatted(currentStatus, newStatus));

        return WorkOrderResponse.from(saved);
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
