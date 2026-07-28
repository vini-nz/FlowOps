package com.flowops.service;

import com.flowops.dto.profile.PasswordChangeRequest;
import com.flowops.dto.profile.ProfileUpdateRequest;
import com.flowops.entity.Company;
import com.flowops.entity.User;
import com.flowops.enums.Role;
import com.flowops.exception.BusinessRuleException;
import com.flowops.exception.InvalidCredentialsException;
import com.flowops.repository.UserRepository;
import com.flowops.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Perfil e troca de senha (V2.8). O ponto central é que a troca de senha
 * marque {@code passwordChangedAt} — é isso que faz o filtro JWT recusar
 * tokens antigos; sem essa marca a troca só mudaria o hash e deixaria
 * sessões abertas com a senha antiga funcionando até expirarem.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private ProfileService service;

    private static final String SENHA_ATUAL = "FlowOps@123";
    private static final Long USER_ID = 9L;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ProfileService(userRepository, passwordEncoder, jwtService);

        Company company = new Company();
        company.setId(1L);
        company.setName("Marcenaria Exemplo");

        user = new User();
        user.setId(USER_ID);
        user.setUuid(UUID.randomUUID());
        user.setCompany(company);
        user.setName("Operador Demonstração");
        user.setEmail("operador@flowops.dev");
        user.setPasswordHash(passwordEncoder.encode(SENHA_ATUAL));
        user.setRole(Role.OPERADOR);

        when(userRepository.findWithCompanyById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(), anyString(), any(), anyString())).thenReturn("token-novo");
    }

    @Test
    void changePassword_marksTheCutoffThatInvalidatesOldTokens() {
        var response = service.changePassword(USER_ID,
                new PasswordChangeRequest(SENHA_ATUAL, "NovaSenha@456"));

        assertThat(user.getPasswordChangedAt()).isNotNull();
        assertThat(passwordEncoder.matches("NovaSenha@456", user.getPasswordHash())).isTrue();
        // Token novo para a sessao que trocou nao se auto-desconectar
        assertThat(response.accessToken()).isEqualTo("token-novo");
    }

    @Test
    void changePassword_rejectsWrongCurrentPassword() {
        assertThatThrownBy(() -> service.changePassword(USER_ID,
                new PasswordChangeRequest("senha-errada", "NovaSenha@456")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.getPasswordChangedAt()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_rejectsReusingTheSamePassword() {
        assertThatThrownBy(() -> service.changePassword(USER_ID,
                new PasswordChangeRequest(SENHA_ATUAL, SENHA_ATUAL)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("diferente da atual");

        assertThat(user.getPasswordChangedAt()).isNull();
    }

    @Test
    void update_changingOnlyNameDoesNotRequirePasswordNorIssueToken() {
        var response = service.update(USER_ID,
                new ProfileUpdateRequest("Novo Nome", "operador@flowops.dev", null));

        assertThat(user.getName()).isEqualTo("Novo Nome");
        // Sem troca de e-mail o token atual continua valido - nao ha o que reemitir
        assertThat(response.accessToken()).isNull();
    }

    @Test
    void update_changingEmailRequiresCurrentPassword() {
        assertThatThrownBy(() -> service.update(USER_ID,
                new ProfileUpdateRequest("Operador", "novo@flowops.dev", null)))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("senha atual");

        assertThat(user.getEmail()).isEqualTo("operador@flowops.dev");
    }

    @Test
    void update_changingEmailIssuesNewTokenBecauseSubjectIsTheEmail() {
        when(userRepository.existsByEmailIgnoreCase("novo@flowops.dev")).thenReturn(false);

        var response = service.update(USER_ID,
                new ProfileUpdateRequest("Operador", "novo@flowops.dev", SENHA_ATUAL));

        assertThat(user.getEmail()).isEqualTo("novo@flowops.dev");
        // O sub do JWT e o e-mail: sem token novo o usuario levaria 401 na
        // requisicao seguinte.
        assertThat(response.accessToken()).isEqualTo("token-novo");
    }

    @Test
    void update_rejectsEmailAlreadyInUse() {
        when(userRepository.existsByEmailIgnoreCase("ocupado@flowops.dev")).thenReturn(true);

        assertThatThrownBy(() -> service.update(USER_ID,
                new ProfileUpdateRequest("Operador", "ocupado@flowops.dev", SENHA_ATUAL)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já está em uso");

        assertThat(user.getEmail()).isEqualTo("operador@flowops.dev");
    }

    @Test
    void update_normalizesEmailToLowercase() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);

        service.update(USER_ID,
                new ProfileUpdateRequest("Operador", "  NOVO@FlowOps.DEV  ", SENHA_ATUAL));

        assertThat(user.getEmail()).isEqualTo("novo@flowops.dev");
    }
}
