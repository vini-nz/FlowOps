package com.flowops.dto.evidence;

import java.util.UUID;

/**
 * O cliente envia o arquivo diretamente para {@code uploadUrl} (PUT) e depois
 * chama o endpoint de confirmação com {@code evidenceUuid}.
 */
public record EvidenceUploadUrlResponse(
        UUID evidenceUuid,
        String uploadUrl,
        int expiresInMinutes
) {}
