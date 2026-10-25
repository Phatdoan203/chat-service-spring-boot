package com.side_project.chat_service.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.side_project.chat_service.domain.User;

public record UserResponse(UUID id, String username, String displayName, String email, String avatarUrl,
        Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.getAvatarUrl(),
                u.getCreatedAt());
    }
}
