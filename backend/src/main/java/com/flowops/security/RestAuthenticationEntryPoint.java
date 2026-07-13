package com.flowops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bug encontrado testando a Sprint 4 (afeta as Sprints 1-3 tambem): sem este
 * entry point, o Spring Security cai no fallback padrao Http403ForbiddenEntryPoint
 * quando nao ha token (ou o token e invalido/expirado) - porque form login e
 * http basic estao desabilitados (JWT stateless), o unico entry point que o
 * framework registra por padrao e o de 403. docs/api.md sempre documentou 401
 * para "token ausente, invalido ou expirado", e o comentario em
 * JwtAuthenticationFilter ja assumia esse comportamento sem garanti-lo.
 * Corrigido registrando este entry point explicitamente no SecurityConfig,
 * no mesmo formato de resposta do GlobalExceptionHandler.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        body.put("message", "Token ausente, inválido ou expirado");
        body.put("timestamp", OffsetDateTime.now().toString());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
