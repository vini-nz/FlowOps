package com.flowops.dto.client;

import com.flowops.entity.Client;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientResponse(
        UUID uuid,
        String name,
        String email,
        String phone,
        String document,
        String notes,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getUuid(),
                client.getName(),
                client.getEmail(),
                client.getPhone(),
                client.getDocument(),
                client.getNotes(),
                client.isActive(),
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }
}
