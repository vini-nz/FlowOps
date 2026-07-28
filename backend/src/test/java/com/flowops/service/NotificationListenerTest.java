package com.flowops.service;

import com.flowops.entity.Company;
import com.flowops.entity.Notification;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.event.WorkOrderEventOccurred;
import com.flowops.repository.NotificationRepository;
import com.flowops.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regras de quem recebe notificação (V2.7). A parte transacional
 * (AFTER_COMMIT / REQUIRES_NEW) é responsabilidade do Spring; o que se testa
 * aqui é a decisão de negócio: quais eventos notificam e quem é avisado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationListenerTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private WorkOrderRepository workOrderRepository;

    private NotificationListener listener;

    private static final Long COMPANY_ID = 1L;
    private static final Long ACTOR_ID = 9L;
    private static final Long ASSIGNEE_ID = 77L;

    private WorkOrder workOrder;
    private User assignee;

    @BeforeEach
    void setUp() {
        // Formatter real: a mensagem da notificacao e parte do comportamento
        // observavel, nao vale a pena mocka-la.
        listener = new NotificationListener(notificationRepository, workOrderRepository,
                new TimelineDescriptionFormatter(new com.fasterxml.jackson.databind.ObjectMapper()));

        Company company = new Company();
        company.setId(COMPANY_ID);

        assignee = new User();
        assignee.setId(ASSIGNEE_ID);
        assignee.setName("Técnico Demonstração");

        workOrder = new WorkOrder();
        workOrder.setId(10L);
        workOrder.setUuid(UUID.randomUUID());
        workOrder.setCompany(company);
        workOrder.setTitle("Armário planejado");
        workOrder.setAssignedTo(assignee);

        when(workOrderRepository.findById(10L)).thenReturn(Optional.of(workOrder));
    }

    private WorkOrderEventOccurred event(String type, String payload, Long actorId) {
        return new WorkOrderEventOccurred(10L, COMPANY_ID, type, payload, actorId, "Armário planejado");
    }

    @Test
    void statusChangeNotifiesTheAssignee() {
        listener.on(event("STATUS_ALTERADO",
                "{\"de\":\"APROVADO\",\"para\":\"EM_EXECUCAO\"}", ACTOR_ID));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(assignee);
        assertThat(saved.getEventType()).isEqualTo("STATUS_ALTERADO");
        // Mensagem legivel, nao o enum cru
        assertThat(saved.getMessage()).contains("Armário planejado", "Aprovado", "Em execução");
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void doesNotNotifyTheActorAboutTheirOwnAction() {
        // O responsavel mexeu na propria WorkOrder: avisa-lo do que ele
        // acabou de fazer e ruido puro.
        listener.on(event("STATUS_ALTERADO",
                "{\"de\":\"APROVADO\",\"para\":\"EM_EXECUCAO\"}", ASSIGNEE_ID));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void doesNotNotifyWhenThereIsNoAssignee() {
        workOrder.setAssignedTo(null);

        listener.on(event("STATUS_ALTERADO", "{\"de\":\"APROVADO\",\"para\":\"EM_EXECUCAO\"}", ACTOR_ID));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void assignmentNotifiesTheAssignee() {
        listener.on(event("RESPONSAVEL_ATRIBUIDO",
                "{\"assignedTo\":\"Técnico Demonstração\"}", ACTOR_ID));

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void routineEventsDoNotBecomeNotifications() {
        // Notificar cada item de checklist ou evidencia transformaria o sino
        // em ruido - so status e atribuicao entram (criterios do backlog).
        listener.on(event("CHECKLIST_ITEM_MARCADO", "{\"etapa\":\"Produção\",\"item\":\"x\"}", ACTOR_ID));
        listener.on(event("EVIDENCIA_ANEXADA", "{\"etapa\":\"Produção\",\"arquivo\":\"foto.png\"}", ACTOR_ID));
        listener.on(event("ITEM_ADICIONADO", "{\"description\":\"Hora\"}", ACTOR_ID));

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void failureToNotifyDoesNotPropagate() {
        // Notificacao e efeito colateral: nao pode derrubar uma operacao de
        // negocio que ja foi confirmada no banco.
        when(notificationRepository.save(any())).thenThrow(new RuntimeException("banco fora"));

        listener.on(event("STATUS_ALTERADO", "{\"de\":\"APROVADO\",\"para\":\"EM_EXECUCAO\"}", ACTOR_ID));
        // sem excecao propagada = teste passa
    }
}
