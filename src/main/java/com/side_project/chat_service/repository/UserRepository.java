package com.side_project.chat_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.side_project.chat_service.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    // q da lowercase + bao %...%, hoac null = khong loc. Sort on dinh cho bo chon o FE.
    @Query("""
            SELECT u FROM User u
            WHERE :q IS NULL OR LOWER(u.username) LIKE :q OR LOWER(u.displayName) LIKE :q
            ORDER BY u.username ASC, u.id ASC""")
    List<User> search(@Param("q") String q, Pageable page);
}
