package com.flowops.dto.workorderstep;

import jakarta.validation.constraints.NotNull;

public record StepChecklistItemToggleRequest(@NotNull Boolean done) {}
