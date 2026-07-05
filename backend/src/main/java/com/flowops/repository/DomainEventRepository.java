package com.flowops.repository;

import com.flowops.entity.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DomainEventRepository extends JpaRepository<DomainEvent, Long> {
    List<DomainEvent> findByWorkOrderIdOrderByOccurredAtAsc(Long workOrderId);
}
