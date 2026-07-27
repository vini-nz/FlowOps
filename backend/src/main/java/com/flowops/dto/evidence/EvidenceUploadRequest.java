package com.flowops.dto.evidence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EvidenceUploadRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 120) String contentType,
        @NotNull @Positive Long sizeBytes
) {}
