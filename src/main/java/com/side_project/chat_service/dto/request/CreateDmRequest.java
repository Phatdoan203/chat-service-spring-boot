package com.side_project.chat_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDmRequest(
        @Schema(description = "Nguoi con lai cua DM; khong duoc trung voi X-User-Id", example = "u-20931")
        @NotBlank @Size(max = 64) String otherUserId) implements CreateConversationRequest {
}
