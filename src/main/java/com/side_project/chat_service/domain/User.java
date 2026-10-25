package com.side_project.chat_service.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    private UUID id;

    private String username;

    private String displayName;

    private String email;

    private String avatarUrl;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    public User(UUID id, String username, String displayName, String email, String avatarUrl) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }
}
