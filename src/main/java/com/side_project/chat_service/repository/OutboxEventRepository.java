package com.side_project.chat_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.side_project.chat_service.domain.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
