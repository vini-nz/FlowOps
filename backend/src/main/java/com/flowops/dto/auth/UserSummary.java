package com.flowops.dto.auth;

import java.util.UUID;

public record UserSummary(
        UUID uuid,
        String name,
        String email,
        String role,
        Long companyId,
        String companyName
) {}
