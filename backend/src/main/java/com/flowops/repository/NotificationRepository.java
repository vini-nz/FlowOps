package com.flowops.repository;

import com.flowops.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Sempre por user_id: uma notificacao pertence a uma pessoa, nao a empresa
    // inteira. O filtro por company_id vem junto como defesa em profundidade -
    // se um id de usuario vazasse de outro tenant, ainda assim nao resolveria.
    @EntityGraph(attributePaths = "workOrder")
    Page<Notification> findByUserIdAndCompanyIdOrderByCreatedAtDesc(
            Long userId, Long companyId, Pageable pageable);

    long countByUserIdAndCompanyIdAndReadFalse(Long userId, Long companyId);

    Optional<Notification> findByUuidAndUserIdAndCompanyId(UUID uuid, Long userId, Long companyId);

    List<Notification> findByUserIdAndCompanyIdAndReadFalse(Long userId, Long companyId);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Notification n
               SET n.read = true, n.readAt = :readAt
             WHERE n.user.id = :userId AND n.company.id = :companyId AND n.read = false
            """)
    int markAllAsRead(Long userId, Long companyId, OffsetDateTime readAt);
}
