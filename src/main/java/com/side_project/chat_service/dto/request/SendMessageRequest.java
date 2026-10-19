package com.side_project.chat_service.dto.request;

import com.side_project.chat_service.enums.MessageType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @Schema(description = "Client sinh mot lan, giu nguyen qua moi lan retry", example = "c-7")
        @NotBlank @Size(max = 64) String clientMessageId,
        @NotNull MessageType type,
        @Schema(description = "Bat buoc voi TEXT", example = "Don hang DH-5512 da ban giao cho shipper")
        @Size(max = 4000) String body,
        @Schema(description = "Bat buoc voi IMAGE/FILE") @Size(max = 64) String attachmentId) {
}
