package com.flowops.dto.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code currentPassword} só é exigida quando o e-mail muda — validado em
 * {@code ProfileService}, não aqui, porque a obrigatoriedade depende de
 * comparar com o valor atual no banco.
 */
public record ProfileUpdateRequest(
        @NotBlank(message = "Informe o nome") @Size(max = 150) String name,
        @NotBlank(message = "Informe o e-mail")
        @Email(message = "E-mail inválido")
        @Size(max = 150) String email,
        String currentPassword
) {
    /**
     * Apara os campos no construtor canônico, antes de o Bean Validation
     * rodar. Sem isso um e-mail colado com espaço sobrando era recusado com
     * "E-mail inválido" — confuso, porque o endereço em si está correto.
     */
    public ProfileUpdateRequest {
        name = name != null ? name.trim() : null;
        email = email != null ? email.trim() : null;
    }
}
