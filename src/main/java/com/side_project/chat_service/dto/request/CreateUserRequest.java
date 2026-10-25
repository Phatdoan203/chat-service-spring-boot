package com.side_project.chat_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @Schema(example = "an.nguyen") @NotBlank @Size(max = 64) String username,
        @Schema(example = "Nguyen Van An") @NotBlank @Size(max = 100) String displayName,
        @Schema(example = "an@example.com", nullable = true) @Email @Size(max = 254) String email,
        @Size(max = 512) String avatarUrl) {
}
