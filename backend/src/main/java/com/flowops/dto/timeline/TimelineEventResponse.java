package com.flowops.dto.timeline;

import java.time.OffsetDateTime;

/**
 * Evento da Timeline (V2.3). {@code description} ja vem legivel do backend -
 * o frontend nunca interpreta domain_events.payload (ver
 * TimelineDescriptionFormatter e docs/architecture.md).
 */
public record TimelineEventResponse(
        String eventType,
        String description,
        String actorName,
        OffsetDateTime occurredAt
) {}
