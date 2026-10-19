package com.side_project.chat_service.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// toi da 50 nguoi gom ca nguoi tao (04-du-lieu-va-api.md)
public record CreateGroupRequest(
        @Schema(example = "Van hanh kho HCM") @NotBlank @Size(max = 200) String title,
        @Schema(description = "Thanh vien them vao, 1-49 nguoi; nguoi tao tu thanh OWNER, id trung hoac trung nguoi tao bi bo qua",
                example = "[\"u-20931\", \"u-30512\"]")
        @NotEmpty @Size(max = 49) List<@NotBlank @Size(max = 64) String> memberIds)
        implements CreateConversationRequest {
}
