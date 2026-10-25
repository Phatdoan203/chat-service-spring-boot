package com.side_project.chat_service.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.side_project.chat_service.dto.request.CreateConversationRequest;
import com.side_project.chat_service.dto.response.ConversationResponse;

public interface ConversationService {

    /** created = true khi hoi thoai vua duoc tao, false khi da ton tai. */
    record Opened(ConversationResponse conversation, boolean created) {
    }

    /** Tu chon nhanh DM (idempotent) hay nhom theo loai request. */
    Opened create(UUID me, CreateConversationRequest request);

    /** Moi nhat truoc; cursor = last_message_at cua phan tu cuoi trang truoc, null = trang dau. */
    List<ConversationResponse> listMine(UUID me, int limit, Instant cursor);
}
