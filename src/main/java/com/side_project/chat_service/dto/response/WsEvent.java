package com.side_project.chat_service.dto.response;

import java.util.UUID;

/** Khung chung cho moi event day xuong /user/queue/chat; client phan nhanh theo type. */
public record WsEvent<T>(String type, UUID conversationId, T payload) {
}
