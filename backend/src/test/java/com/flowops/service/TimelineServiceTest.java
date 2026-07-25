package com.flowops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.entity.DomainEvent;
import com.flowops.entity.User;
import com.flowops.entity.WorkOrder;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.DomainEventRepository;
import com.flowops.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimelineServiceTest {

    @Mock
    private DomainEventRepository domainEventRepository;
    @Mock
    private WorkOrderRepository workOrderRepository;

    private TimelineService timelineService;

    private static final Long COMPANY_ID = 1L;

    @BeforeEach
    void setUp() {
        timelineService = new TimelineService(
                domainEventRepository, workOrderRepository,
                new TimelineDescriptionFormatter(new ObjectMapper()));
    }

    @Test
    void list_returnsEventsWithDescriptionActorAndTimestamp() {
        WorkOrder wo = new WorkOrder();
        wo.setId(10L);
        wo.setUuid(UUID.randomUUID());

        User actor = new User();
        actor.setName("Operador Demonstração");

        DomainEvent created = new DomainEvent();
        created.setEventType("WORKORDER_CRIADA");
        created.setActor(actor);

        DomainEvent statusChanged = new DomainEvent();
        statusChanged.setEventType("STATUS_ALTERADO");
        statusChanged.setPayload("{\"de\":\"SOLICITACAO_RECEBIDA\",\"para\":\"ORCAMENTO_GERADO\"}");
        statusChanged.setActor(actor);

        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(domainEventRepository.findByWorkOrderIdOrderByOccurredAtAscIdAsc(wo.getId()))
                .thenReturn(List.of(created, statusChanged));

        var timeline = timelineService.list(COMPANY_ID, wo.getUuid());

        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).description()).isEqualTo("Ordem de serviço criada");
        assertThat(timeline.get(0).actorName()).isEqualTo("Operador Demonstração");
        assertThat(timeline.get(1).description())
                .isEqualTo("Status alterado de Solicitação recebida para Orçamento gerado");
    }

    @Test
    void list_eventWithoutActorReturnsNullActorName() {
        WorkOrder wo = new WorkOrder();
        wo.setId(10L);
        wo.setUuid(UUID.randomUUID());

        DomainEvent event = new DomainEvent();
        event.setEventType("WORKORDER_CRIADA");

        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(domainEventRepository.findByWorkOrderIdOrderByOccurredAtAscIdAsc(wo.getId()))
                .thenReturn(List.of(event));

        assertThat(timelineService.list(COMPANY_ID, wo.getUuid()).get(0).actorName()).isNull();
    }

    @Test
    void list_whenWorkOrderBelongsToAnotherCompany_throwsAndNeverReadsEvents() {
        UUID workOrderUuid = UUID.randomUUID();
        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(workOrderUuid, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> timelineService.list(COMPANY_ID, workOrderUuid))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(domainEventRepository, never()).findByWorkOrderIdOrderByOccurredAtAscIdAsc(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void list_preservesRepositoryOrderForEventsSharingTheSameTimestamp() {
        // Eventos gravados na mesma transacao compartilham occurred_at (now()
        // e o inicio da transacao no PostgreSQL) - a ordem correta vem do
        // desempate por id feito na query, e o Service nao pode reordenar.
        WorkOrder wo = new WorkOrder();
        wo.setId(10L);
        wo.setUuid(UUID.randomUUID());

        DomainEvent statusToAguardando = new DomainEvent();
        statusToAguardando.setEventType("STATUS_ALTERADO");
        statusToAguardando.setPayload("{\"de\":\"ORCAMENTO_GERADO\",\"para\":\"AGUARDANDO_APROVACAO\"}");

        DomainEvent statusToAprovado = new DomainEvent();
        statusToAprovado.setEventType("STATUS_ALTERADO");
        statusToAprovado.setPayload("{\"de\":\"AGUARDANDO_APROVACAO\",\"para\":\"APROVADO\"}");

        DomainEvent budgetApproved = new DomainEvent();
        budgetApproved.setEventType("ORCAMENTO_APROVADO");

        when(workOrderRepository.findByUuidAndCompanyIdAndDeletedAtIsNull(wo.getUuid(), COMPANY_ID))
                .thenReturn(Optional.of(wo));
        when(domainEventRepository.findByWorkOrderIdOrderByOccurredAtAscIdAsc(wo.getId()))
                .thenReturn(List.of(statusToAguardando, statusToAprovado, budgetApproved));

        var timeline = timelineService.list(COMPANY_ID, wo.getUuid());

        assertThat(timeline).extracting("eventType")
                .containsExactly("STATUS_ALTERADO", "STATUS_ALTERADO", "ORCAMENTO_APROVADO");
    }
}
