package com.flowops.service;

import com.flowops.dto.profile.PasswordChangeRequest;
import com.flowops.dto.profile.ProfileResponse;
import com.flowops.dto.profile.ProfileUpdateRequest;
import com.flowops.entity.User;
import com.flowops.exception.BusinessRuleException;
import com.flowops.exception.InvalidCredentialsException;
import com.flowops.exception.ResourceNotFoundException;
import com.flowops.repository.UserRepository;
import com.flowops.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Perfil do próprio usuário (V2.8 — item 13 do Backlog, parte antecipada
 * para a V2; 2FA e sessões ativas ficam na V3).
 * <p>
 * O usuário sempre vem do token, nunca de parâmetro: não existe "editar o
 * perfil de outra pessoa" aqui. Gerenciar usuários da empresa é outro item,
 * ainda não implementado.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        return ProfileResponse.from(findOrThrow(userId));
    }

    /**
     * Atualiza nome e e-mail. Trocar o e-mail exige a senha atual: o e-mail é
     * a credencial de login, então permitir a troca só com um token válido
     * transformaria qualquer sessão sequestrada em tomada de conta definitiva.
     * <p>
     * Como o {@code sub} do JWT é o e-mail, o token antigo deixa de resolver
     * assim que o e-mail muda — por isso um token novo é devolvido junto,
     * para a sessão atual continuar de pé.
     */
    @Transactional
    public ProfileResponse update(Long userId, ProfileUpdateRequest request) {
        User user = findOrThrow(userId);

        String newEmail = request.email().trim().toLowerCase(Locale.ROOT);
        boolean emailChanged = !newEmail.equalsIgnoreCase(user.getEmail());

        if (emailChanged) {
            assertCurrentPassword(user, request.currentPassword(),
                    "Informe a senha atual para alterar o e-mail");

            // E-mail e globalmente unico desde a correcao da Sprint 2
            // (ver ADR-0001) - checamos antes para devolver mensagem legivel
            // em vez de deixar estourar a constraint.
            if (userRepository.existsByEmailIgnoreCase(newEmail)) {
                throw new BusinessRuleException("Este e-mail já está em uso");
            }
            user.setEmail(newEmail);
        }

        user.setName(request.name().trim());
        User saved = userRepository.save(user);

        String newToken = emailChanged ? issueToken(saved) : null;
        return ProfileResponse.from(saved, newToken);
    }

    /**
     * Troca de senha. Marca {@code passwordChangedAt}, o que invalida todo
     * JWT emitido antes deste instante (ver JwtAuthenticationFilter) — é o
     * que faz a troca de senha realmente expulsar uma sessão aberta com a
     * senha antiga, em vez de apenas mudar o hash.
     * <p>
     * A sessão que fez a troca recebe um token novo para não se
     * auto-desconectar.
     */
    @Transactional
    public ProfileResponse changePassword(Long userId, PasswordChangeRequest request) {
        User user = findOrThrow(userId);

        assertCurrentPassword(user, request.currentPassword(), "Senha atual incorreta");

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("A nova senha deve ser diferente da atual");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(OffsetDateTime.now());
        User saved = userRepository.save(user);

        return ProfileResponse.from(saved, issueToken(saved));
    }

    private void assertCurrentPassword(User user, String currentPassword, String message) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException(message);
        }
    }

    private String issueToken(User user) {
        return jwtService.generateToken(
                user.getId(), user.getEmail(), user.getCompany().getId(), user.getRole().name());
    }

    private User findOrThrow(Long userId) {
        return userRepository.findWithCompanyById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }
}
