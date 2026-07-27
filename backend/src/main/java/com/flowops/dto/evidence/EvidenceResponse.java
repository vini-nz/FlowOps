package com.flowops.dto.evidence;

import com.flowops.entity.Evidence;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EvidenceResponse(
        UUID uuid,
        String fileName,
        String contentType,
        Long sizeBytes,
        boolean image,
        String uploadedByName,
        OffsetDateTime uploadedAt
) {
    public static EvidenceResponse from(Evidence evidence) {
        return new EvidenceResponse(
                evidence.getUuid(),
                evidence.getFileName(),
                evidence.getContentType(),
                evidence.getSizeBytes(),
                evidence.getContentType() != null && evidence.getContentType().startsWith("image/"),
                evidence.getUploadedBy() != null ? evidence.getUploadedBy().getName() : null,
                evidence.getUploadedAt()
        );
    }
}
