package com.group19.teaching.domain.dto;

import javax.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String account,
        @NotBlank String password,
        @NotBlank String role
) {
}
