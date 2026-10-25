package com.side_project.chat_service.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.side_project.chat_service.common.util.LogEvent;
import com.side_project.chat_service.dto.request.CreateUserRequest;
import com.side_project.chat_service.dto.response.UserResponse;
import com.side_project.chat_service.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Users", description = "Tao va xem nguoi dung; `id` (UUID) la gia tri dung cho header X-User-Id")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @Operation(summary = "Tao nguoi dung", description = """
            ```
            curl -i -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' \
              -d '{"username":"an.nguyen","displayName":"Nguyen Van An","email":"an@example.com"}'
            ```
            Mong doi: `201` kem `id` (UUID). Chay lai cung lenh: `409` (`code=CONFLICT`) vi trung `username`.
            Thieu `username`/`displayName` hoac `email` sai dinh dang: `422`.
            """)
    @ApiResponse(responseCode = "201", description = "Da tao")
    @ApiResponse(responseCode = "409", description = "Trung username hoac email")
    @ApiResponse(responseCode = "422", description = "Body sai")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        var u = service.create(request);
        log.info(LogEvent.of("user.created", "userId", u.id(), "username", u.username()));
        return u;
    }

    @Operation(summary = "Xem nguoi dung", description = """
            ```
            curl localhost:8080/api/v1/users/$USER_ID
            ```
            Mong doi: `200`. Id khong ton tai: `404`. Id khong phai UUID: `400`.
            """)
    @ApiResponse(responseCode = "404", description = "Khong ton tai")
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Danh sach / tim nguoi dung", description = """
            Phuc vu man chon user o FE truoc khi vao chat (lay `id` dat vao header X-User-Id). \
            `query` khop khong phan biet hoa thuong tren `username` hoac `displayName`; bo trong = khong loc. \
            `limit` mac dinh 50, kep ve 1..100. Sap xep `username ASC, id ASC`.
            ```
            curl 'localhost:8888/api/v1/users'
            curl 'localhost:8888/api/v1/users?query=AN&limit=10'
            ```
            Mong doi: `200` kem mang `UserResponse` (rong `[]` neu khong co ai). `limit=0` thanh 1; `limit>100` thanh 100.
            """)
    @ApiResponse(responseCode = "200", description = "Mang UserResponse, rong neu khong khop")
    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) String query,
            @RequestParam(defaultValue = "50") int limit) {
        return service.list(query, limit);
    }
}
