package com.flowops.repository;

import com.flowops.entity.DomainEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {

    // Desempate por id e obrigatorio, nao cosmetico: occurred_at usa
    // DEFAULT now(), que no PostgreSQL devolve o inicio da TRANSACAO, nao o
    // instante do INSERT. Varios eventos gravados na mesma transacao (ex:
    // BudgetService.updateStatus grava ORCAMENTO_APROVADO e duas transicoes
    // de status) ficam com occurred_at identico, e so occurred_at deixaria a
    // ordem entre eles indefinida - a Timeline mostraria a aprovacao antes
    // das transicoes que a causaram. id e BIGSERIAL, entao preserva a ordem
    // real de insercao.
    //
    // actor e LAZY e a Timeline (V2.3) acessa actor.getName() de cada evento -
    // sem EntityGraph seria uma query extra por evento (N+1), justamente no
    // endpoint que tende a ter o maior numero de linhas por WorkOrder.
    // actor pode ser null (evento sem autor), o que o EntityGraph tolera.
    @EntityGraph(attributePaths = "actor")
    List<DomainEvent> findByWorkOrderIdOrderByOccurredAtAscIdAsc(Long workOrderId);
}
