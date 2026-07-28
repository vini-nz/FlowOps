package com.flowops.service;

import com.flowops.entity.DomainEvent;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.event.WorkOrderEventOccurred;
import com.flowops.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Único ponto do sistema que grava em {@code domain_events} (V2.7).
 * <p>
 * Até a V2.6 cada serviço (WorkOrder, Budget, WorkOrderStep, Evidence) tinha
 * sua própria cópia privada de {@code recordEvent} — quatro implementações
 * idênticas, registradas como dívida técnica em docs/architecture.md desde a
 * V2.1. Notificações precisariam se plugar em todas elas, o que tornaria a
 * duplicação um problema real e não só estético; por isso a extração aconteceu
 * agora.
 * <p>
 * Além de persistir o evento, publica um {@link WorkOrderEventOccurred} para
 * quem quiser reagir (hoje: notificações). Os consumidores usam
 * {@code AFTER_COMMIT}, então nada é notificado se a transação der rollback —
 * o usuário nunca recebe aviso de algo que não aconteceu.
 */
@Service
@RequiredArgsConstructor
public class DomainEventService {

    private final DomainEventRepository domainEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void record(WorkOrder workOrder, String eventType, User actor, String payload) {
        DomainEvent event = new DomainEvent();
        event.setWorkOrder(workOrder);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setPayload(payload);
        domainEventRepository.save(event);

        applicationEventPublisher.publishEvent(new WorkOrderEventOccurred(
                workOrder.getId(),
                workOrder.getCompany().getId(),
                eventType,
                payload,
                actor != null ? actor.getId() : null,
                workOrder.getTitle()));
    }
}
