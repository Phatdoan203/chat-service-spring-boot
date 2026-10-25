package com.side_project.chat_service.dto.request;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// toi da 50 nguoi gom ca nguoi tao (04-du-lieu-va-api.md)
public record CreateGroupRequest(
        @Schema(example = "Van hanh kho HCM") @NotBlank @Size(max = 200) String title,
        @Schema(description = "Thanh vien them vao, 1-49 nguoi; nguoi tao tu thanh OWNER, id trung hoac trung nguoi tao bi bo qua",
                example = "[\"6f1c2a4e-8b7d-4e5a-9c3f-1a2b3c4d5e6f\"]")
        @NotEmpty @Size(max = 49) List<@NotNull UUID> memberIds)
        implements CreateConversationRequest {
}
