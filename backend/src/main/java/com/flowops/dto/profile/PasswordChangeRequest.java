package com.flowops.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "Informe a senha atual") String currentPassword,
        @NotBlank(message = "Informe a nova senha")
        @Size(min = 8, max = 100, message = "A nova senha deve ter ao menos 8 caracteres")
        String newPassword
) {}
