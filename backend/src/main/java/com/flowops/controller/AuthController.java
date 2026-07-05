package com.flowops.controller;

import com.flowops.dto.auth.LoginRequest;
import com.flowops.dto.auth.LoginResponse;
import com.flowops.dto.auth.UserSummary;
import com.flowops.entity.User;
import com.flowops.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Rota protegida de verificacao: se o JWT for valido, o Spring Security
     * ja preencheu o SecurityContext com o User autenticado antes de chegar aqui.
     * Usada pelo frontend para restaurar a sessao ao recarregar a pagina.
     */
    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal User user) {
        return new UserSummary(
                user.getUuid(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getCompany().getId(),
                user.getCompany().getName()
        );
    }
}
