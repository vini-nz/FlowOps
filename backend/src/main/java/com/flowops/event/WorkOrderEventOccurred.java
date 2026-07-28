package com.flowops.event;

/**
 * Evento de aplicação publicado sempre que algo acontece numa WorkOrder
 * (V2.7). Carrega só valores simples, nunca entidades: os consumidores rodam
 * em {@code @TransactionalEventListener(AFTER_COMMIT)}, ou seja, depois que a
 * transação fechou — uma entidade LAZY passada aqui estaria destacada e
 * qualquer acesso a associação estouraria {@code LazyInitializationException}.
 *
 * @param workOrderId    id interno, para o listener resolver o que precisar
 * @param companyId      tenant, para o listener não precisar recarregar a WorkOrder
 * @param eventType      mesmo vocabulário gravado em {@code domain_events}
 * @param payload        JSON já montado, mesmo contrato da Timeline
 * @param actorId        quem provocou — usado para não notificar o próprio autor
 * @param workOrderTitle título, para a mensagem da notificação não exigir outra query
 */
public record WorkOrderEventOccurred(
        Long workOrderId,
        Long companyId,
        String eventType,
        String payload,
        Long actorId,
        String workOrderTitle
) {}
