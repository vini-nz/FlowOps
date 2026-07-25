package com.flowops.service;

import com.flowops.dto.workorderstep.StepChecklistItemResponse;
import com.flowops.dto.workorderstep.WorkOrderStepResponse;
import com.flowops.dto.workorderstep.WorkOrderStepStatusUpdateRequest;
import com.flowops.entity.DomainEvent;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.entity.WorkOrderStep;
import com.flowops.entity.WorkOrderStepChecklistItem;
import com.flowops.enums.StepStatus;
import com.flowops.enums.WorkOrderStatus;
import com.flowops.exception.BusinessRuleException;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.DomainEventRepository;
import com.flowops.repository.WorkOrderRepository;
import com.flowops.repository.WorkOrderStepChecklistItemRepository;
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
    private final WorkOrderStepChecklistItemRepository checklistItemRepository;

    @Transactional(readOnly = true)
    public List<WorkOrderStepResponse> list(Long companyId, UUID workOrderUuid) {
        WorkOrder workOrder = findOwnedWorkOrderOrThrow(companyId, workOrderUuid);
        return workOrderStepRepository.findByWorkOrderIdOrderByStepOrderAsc(workOrder.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Marca ou desmarca um item de checklist (V2.5). Não mexe no status da
     * etapa — o critério de aceitação do backlog é explícito quanto a isso:
     * "criar e marcar itens de checklist sem mudar status".
     */
    @Transactional
    public WorkOrderStepResponse toggleChecklistItem(
            Long companyId, UUID workOrderUuid, UUID stepUuid, UUID itemUuid, boolean done, User actor) {
        WorkOrder workOrder = findOwnedWorkOrderOrThrow(companyId, workOrderUuid);
        WorkOrderStep step = findStepOrThrow(workOrder, stepUuid);
        assertWorkOrderIsExecutable(workOrder);
        assertStepIsOpen(step);

        WorkOrderStepChecklistItem item = checklistItemRepository
                .findByUuidAndWorkOrderStepId(itemUuid, step.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Item de checklist não encontrado"));

        item.setDone(done);
        item.setDoneAt(done ? OffsetDateTime.now() : null);
        item.setDoneBy(done ? actor : null);
        checklistItemRepository.save(item);

        recordEvent(workOrder, done ? "CHECKLIST_ITEM_MARCADO" : "CHECKLIST_ITEM_DESMARCADO", actor,
                "{\"etapa\":\"%s\",\"item\":\"%s\"}".formatted(step.getTitle(), item.getDescription()));

        return toResponse(step);
    }

    /** Item avulso, específico desta OS — não volta para o molde. */
    @Transactional
    public WorkOrderStepResponse addChecklistItem(
            Long companyId, UUID workOrderUuid, UUID stepUuid, String description, User actor) {
        WorkOrder workOrder = findOwnedWorkOrderOrThrow(companyId, workOrderUuid);
        WorkOrderStep step = findStepOrThrow(workOrder, stepUuid);
        assertWorkOrderIsExecutable(workOrder);
        assertStepIsOpen(step);

        WorkOrderStepChecklistItem item = new WorkOrderStepChecklistItem();
        item.setWorkOrderStep(step);
        item.setDescription(description);
        item.setItemOrder(checklistItemRepository
                .findFirstByWorkOrderStepIdOrderByItemOrderDesc(step.getId())
                .map(last -> last.getItemOrder() + 1)
                .orElse(1));
        checklistItemRepository.save(item);

        recordEvent(workOrder, "CHECKLIST_ITEM_ADICIONADO", actor,
                "{\"etapa\":\"%s\",\"item\":\"%s\"}".formatted(step.getTitle(), description));

        return toResponse(step);
    }

    /** Só itens avulsos podem sair — remover um item do molde numa OS
     *  específica esconderia uma exigência que a empresa definiu. */
    @Transactional
    public WorkOrderStepResponse removeChecklistItem(
            Long companyId, UUID workOrderUuid, UUID stepUuid, UUID itemUuid, User actor) {
        WorkOrder workOrder = findOwnedWorkOrderOrThrow(companyId, workOrderUuid);
        WorkOrderStep step = findStepOrThrow(workOrder, stepUuid);
        assertWorkOrderIsExecutable(workOrder);
        assertStepIsOpen(step);

        WorkOrderStepChecklistItem item = checklistItemRepository
                .findByUuidAndWorkOrderStepId(itemUuid, step.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Item de checklist não encontrado"));

        if (item.getWorkflowChecklistItem() != null) {
            throw new BusinessRuleException(
                    "Este item vem do workflow da empresa e não pode ser removido de uma Ordem de Serviço");
        }

        checklistItemRepository.delete(item);

        return toResponse(step);
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

        return toResponse(saved);
    }

    private WorkOrder findOwnedWorkOrderOrThrow(Long companyId, UUID workOrderUuid) {
        return workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(workOrderUuid, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder não encontrada"));
    }

    private WorkOrderStep findStepOrThrow(WorkOrder workOrder, UUID stepUuid) {
        return workOrderStepRepository.findByUuidAndWorkOrderId(stepUuid, workOrder.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Etapa não encontrada"));
    }

    // CONCLUIDA e terminal para a etapa (StepStatusTransitions) - o checklist
    // segue a mesma regra, senao seria possivel reescrever a evidencia de um
    // trabalho ja dado por encerrado.
    private void assertStepIsOpen(WorkOrderStep step) {
        if (step.getStatus() == StepStatus.CONCLUIDA) {
            throw new BusinessRuleException("Etapa concluída — o checklist não pode mais ser alterado");
        }
    }

    private WorkOrderStepResponse toResponse(WorkOrderStep step) {
        List<StepChecklistItemResponse> checklist =
                checklistItemRepository.findByWorkOrderStepIdOrderByItemOrderAsc(step.getId()).stream()
                        .map(StepChecklistItemResponse::from)
                        .toList();
        return WorkOrderStepResponse.from(step, checklist);
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
