package com.side_project.chat_service.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Mot endpoint POST /conversations cho ca DM lan nhom, phan biet bang field type. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CreateDmRequest.class, name = "DM"),
        @JsonSubTypes.Type(value = CreateGroupRequest.class, name = "GROUP")
})
public sealed interface CreateConversationRequest permits CreateDmRequest, CreateGroupRequest {
}
