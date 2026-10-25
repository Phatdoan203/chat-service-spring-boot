package com.side_project.chat_service.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.side_project.chat_service.common.exception.ApiException;
import com.side_project.chat_service.common.exception.ErrorCode;
import com.side_project.chat_service.domain.User;
import com.side_project.chat_service.dto.request.CreateUserRequest;
import com.side_project.chat_service.dto.response.UserResponse;
import com.side_project.chat_service.repository.UserRepository;
import com.side_project.chat_service.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository users;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest r) {
        // De UNIQUE(username)/UNIQUE(email) cua DB quyet, khong SELECT roi INSERT: hai request song song deu
        // qua duoc existsBy nhung chi mot ben commit duoc (coding-standards: day rang buoc xuong DB).
        try {
            var saved = users.saveAndFlush(
                    new User(UUID.randomUUID(), r.username(), r.displayName(), r.email(), r.avatarUrl()));
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.CONFLICT, "username hoac email da ton tai");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return users.findById(id).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> list(String query, int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        String q = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        return users.search(q, PageRequest.of(0, size)).stream().map(UserResponse::from).toList();
    }
}
