package com.flowops.controller;

import com.flowops.dto.profile.PasswordChangeRequest;
import com.flowops.dto.profile.ProfileResponse;
import com.flowops.dto.profile.ProfileUpdateRequest;
import com.flowops.entity.User;
import com.flowops.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Perfil do próprio usuário (V2.8). Sem restrição por papel — todo mundo tem
 * um perfil. O usuário vem sempre do token autenticado, nunca de parâmetro
 * da rota, então não há como editar o perfil de outra pessoa por aqui.
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal User user) {
        return profileService.get(user.getId());
    }

    @PutMapping
    public ProfileResponse update(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return profileService.update(user.getId(), request);
    }

    @PatchMapping("/password")
    public ProfileResponse changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PasswordChangeRequest request) {
        return profileService.changePassword(user.getId(), request);
    }
}
