package com.flowops.dto.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        UserSummary user
) {
    public static LoginResponse of(String token, UserSummary user) {
        return new LoginResponse(token, "Bearer", user);
    }
}
