package com.side_project.chat_service.service;

import java.util.List;

import com.side_project.chat_service.dto.response.MessageCreatedEvent;
import com.side_project.chat_service.dto.response.NotificationEvent;

/** Dung danh sach NotificationEvent cho mot tin: nguoi nhan (tru sender) x kenh dang bat. */
public interface NotificationService {

    List<NotificationEvent> buildFor(MessageCreatedEvent event);
}
