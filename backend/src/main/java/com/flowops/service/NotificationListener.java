package com.flowops.service;

import com.flowops.entity.Notification;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.event.WorkOrderEventOccurred;
import com.flowops.repository.NotificationRepository;
import com.flowops.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.Set;

/**
 * Gera notificações in-app a partir dos eventos de domínio (V2.7).
 * <p>
 * Roda em {@code AFTER_COMMIT}: se a transação que provocou o evento der
 * rollback, nada é notificado — ninguém recebe aviso de algo que não
 * aconteceu. Como a transação original já fechou, o listener abre a sua
 * própria ({@code REQUIRES_NEW}) e recarrega o que precisar pelo id.
 * <p>
 * Uma falha aqui é registrada mas não propagada: notificação é efeito
 * colateral, e não pode derrubar — nem desfazer — a operação de negócio que
 * já foi confirmada.
 */
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    /**
     * Só estes eventos viram notificação. Os critérios de aceitação do
     * backlog pedem mudança de status e atribuição de responsável; notificar
     * cada item de checklist ou evidência transformaria o sino em ruído e
     * faria o usuário parar de olhar — que é o oposto do objetivo.
     */
    private static final Set<String> NOTIFIABLE = Set.of("STATUS_ALTERADO", "RESPONSAVEL_ATRIBUIDO");

    private final NotificationRepository notificationRepository;
    private final WorkOrderRepository workOrderRepository;
    private final TimelineDescriptionFormatter descriptionFormatter;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(WorkOrderEventOccurred event) {
        if (!NOTIFIABLE.contains(event.eventType())) {
            return;
        }

        try {
            Optional<User> recipient = resolveRecipient(event);
            if (recipient.isEmpty()) {
                return;
            }

            WorkOrder workOrder = workOrderRepository.findById(event.workOrderId()).orElse(null);
            if (workOrder == null) {
                return;
            }

            Notification notification = new Notification();
            notification.setCompany(workOrder.getCompany());
            notification.setUser(recipient.get());
            notification.setWorkOrder(workOrder);
            notification.setEventType(event.eventType());
            notification.setMessage(buildMessage(event));
            notificationRepository.save(notification);

        } catch (Exception e) {
            log.error("Falha ao gerar notificacao para o evento {} da WorkOrder {}",
                    event.eventType(), event.workOrderId(), e);
        }
    }

    /**
     * Destinatário é o responsável pela WorkOrder — e nunca quem provocou o
     * evento: avisar alguém da própria ação é ruído puro, e é o erro mais
     * comum nesse tipo de recurso.
     */
    private Optional<User> resolveRecipient(WorkOrderEventOccurred event) {
        return workOrderRepository.findById(event.workOrderId())
                .map(WorkOrder::getAssignedTo)
                .filter(assignee -> !assignee.getId().equals(event.actorId()));
    }

    private String buildMessage(WorkOrderEventOccurred event) {
        String description = descriptionFormatter.describe(event.eventType(), event.payload());
        String message = "%s — %s".formatted(event.workOrderTitle(), description);

        // A coluna e VARCHAR(300); um titulo muito longo nao pode derrubar a
        // gravacao da notificacao inteira.
        return message.length() > 300 ? message.substring(0, 297) + "..." : message;
    }
}
