package com.flowops.dto.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 150) String email,
        @Size(max = 20) String phone,
        @Size(max = 20) String document,
        String notes
) {}
