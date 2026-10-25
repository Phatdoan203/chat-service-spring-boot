package com.side_project.chat_service.dto.request;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CreateDmRequest(
        @Schema(description = "Nguoi con lai cua DM; khong duoc trung voi X-User-Id", example = "6f1c2a4e-8b7d-4e5a-9c3f-1a2b3c4d5e6f")
        @NotNull UUID otherUserId) implements CreateConversationRequest {
}
