package com.flowops.security;

import com.flowops.entity.User;
import com.flowops.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String email = jwtService.extractEmail(token);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findByEmailAndActiveTrue(email).orElse(null);
                UserDetails userDetails = user;

                if (userDetails != null && jwtService.isTokenValid(token, email)
                        && issuedAfterLastPasswordChange(token, user)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ex) {
            // Token invalido, expirado ou malformado: segue sem autenticar.
            // O SecurityConfig ira bloquear com 401 se a rota exigir autenticacao.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Recusa tokens emitidos antes da última troca de senha (V2.8).
     * <p>
     * A comparação trunca {@code passwordChangedAt} para segundos porque o
     * claim {@code iat} do JWT tem precisão de segundo, enquanto a coluna
     * guarda microssegundos: sem truncar, o token novo emitido no mesmo
     * segundo da troca seria recusado junto com os antigos, e quem acabou de
     * trocar a senha levaria 401 imediatamente.
     */
    private boolean issuedAfterLastPasswordChange(String token, User user) {
        OffsetDateTime changedAt = user.getPasswordChangedAt();
        if (changedAt == null) {
            return true;
        }
        Instant cutoff = changedAt.toInstant().truncatedTo(ChronoUnit.SECONDS);
        return !jwtService.extractIssuedAt(token).isBefore(cutoff);
    }
}
