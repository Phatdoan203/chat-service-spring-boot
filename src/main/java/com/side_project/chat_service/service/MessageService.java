package com.side_project.chat_service.service;

import java.util.List;
import java.util.UUID;

import com.side_project.chat_service.dto.request.SendMessageRequest;
import com.side_project.chat_service.dto.response.MessageResponse;

public interface MessageService {

    /** created = false khi clientMessageId da gui truoc do (tra lai tin cu). */
    record Sent(MessageResponse message, boolean created) {
    }

    Sent send(String me, UUID conversationId, SendMessageRequest request);

    /** afterSeq != null: lay bu (cu truoc); nguoc lai: cuon len tu beforeSeq (moi truoc). Tran 200. */
    List<MessageResponse> history(String me, UUID conversationId, Long beforeSeq, Long afterSeq, int limit);

    void markRead(String me, UUID conversationId, long seq);
}
