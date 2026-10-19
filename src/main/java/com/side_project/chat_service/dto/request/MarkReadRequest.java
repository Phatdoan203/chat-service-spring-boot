package com.side_project.chat_service.dto.request;

import jakarta.validation.constraints.Positive;

public record MarkReadRequest(@Positive long seq) {
}
