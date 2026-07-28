package com.flowops.dto.profile;

import com.flowops.entity.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfileResponse(
        UUID uuid,
        String name,
        String email,
        String role,
        String companyName,
        OffsetDateTime lastLoginAt,
        OffsetDateTime passwordChangedAt,
        /** Token novo quando a alteração invalidou o anterior; nulo caso contrário. */
        String accessToken
) {
    public static ProfileResponse from(User user) {
        return from(user, null);
    }

    public static ProfileResponse from(User user, String accessToken) {
        return new ProfileResponse(
                user.getUuid(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getCompany().getName(),
                user.getLastLoginAt(),
                user.getPasswordChangedAt(),
                accessToken
        );
    }
}
