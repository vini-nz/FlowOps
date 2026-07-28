package com.flowops.controller;

import com.flowops.dto.notification.NotificationResponse;
import com.flowops.entity.User;
import com.flowops.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Notificações do próprio usuário (V2.7). Não há restrição por papel: cada
 * um lê e marca as suas, e o destinatário vem sempre do token — nunca de um
 * parâmetro da requisição, que permitiria ler a caixa de outra pessoa.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Page<NotificationResponse> list(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.list(user.getCompany().getId(), user.getId(), pageable);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal User user) {
        return Map.of("count", notificationService.countUnread(user.getCompany().getId(), user.getId()));
    }

    @PatchMapping("/{uuid}/read")
    public NotificationResponse markAsRead(@AuthenticationPrincipal User user, @PathVariable UUID uuid) {
        return notificationService.markAsRead(user.getCompany().getId(), user.getId(), uuid);
    }

    @PatchMapping("/read-all")
    public Map<String, Integer> markAllAsRead(@AuthenticationPrincipal User user) {
        return Map.of("updated", notificationService.markAllAsRead(user.getCompany().getId(), user.getId()));
    }
}
