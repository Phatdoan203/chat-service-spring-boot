package com.side_project.chat_service.service;

import java.util.List;
import java.util.UUID;

import com.side_project.chat_service.dto.request.CreateUserRequest;
import com.side_project.chat_service.dto.response.UserResponse;

public interface UserService {

    UserResponse create(CreateUserRequest request);

    UserResponse get(UUID id);

    List<UserResponse> list(String query, int limit);
}
