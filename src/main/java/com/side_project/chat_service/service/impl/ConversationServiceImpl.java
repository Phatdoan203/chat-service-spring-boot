package com.side_project.chat_service.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.side_project.chat_service.common.exception.ApiException;
import com.side_project.chat_service.common.exception.ErrorCode;
import com.side_project.chat_service.common.util.DmKeys;
import com.side_project.chat_service.domain.Conversation;
import com.side_project.chat_service.domain.ConversationMember;
import com.side_project.chat_service.enums.ConversationType;
import com.side_project.chat_service.enums.MemberRole;
import com.side_project.chat_service.dto.request.CreateConversationRequest;
import com.side_project.chat_service.dto.request.CreateDmRequest;
import com.side_project.chat_service.dto.request.CreateGroupRequest;
import com.side_project.chat_service.dto.response.ConversationResponse;
import com.side_project.chat_service.repository.ConversationMemberRepository;
import com.side_project.chat_service.repository.ConversationRepository;
import com.side_project.chat_service.service.ConversationService;


@Service
public class ConversationServiceImpl implements ConversationService {

    private static final int MAX_LIMIT = 100;
    // thay cho "khong co cursor": moc xa trong tuong lai, tranh param null kieu khong xac dinh trong SQL
    private static final Instant NO_CURSOR = Instant.parse("9999-12-31T00:00:00Z");

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;

    public ConversationServiceImpl(ConversationRepository conversations, ConversationMemberRepository members) {
        this.conversations = conversations;
        this.members = members;
    }

    @Override
    @Transactional
    public Opened create(UUID me, CreateConversationRequest request) {
        return switch (request) {
            case CreateDmRequest dm -> openDirect(me, dm.otherUserId());
            case CreateGroupRequest g -> new Opened(createGroup(me, g.title(), g.memberIds()), true);
        };
    }

    private Opened openDirect(UUID me, UUID otherUserId) {
        if (me.equals(otherUserId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Khong the nhan tin voi chinh minh");
        }
        String dmKey = DmKeys.of(me, otherUserId);
        UUID id = UUID.randomUUID();
        // ON CONFLICT: ben thua cho ben thang commit, nen SELECT sau do luon thay hang day du
        boolean created = conversations.insertDmIfAbsent(id, dmKey, me) == 1;
        if (created) {
            members.insertDmMembers(id, me, otherUserId);
        }
        Conversation c = conversations.findByDmKey(dmKey).orElseThrow();
        return new Opened(ConversationResponse.from(c), created);
    }

    private ConversationResponse createGroup(UUID me, String title, List<UUID> memberIds) {
        var c = conversations.saveAndFlush(new Conversation(UUID.randomUUID(), ConversationType.GROUP, null, title, me));
        long joinSeq = c.getLastSeq();
        members.save(new ConversationMember(c.getId(), me, MemberRole.OWNER, joinSeq));
        memberIds.stream().distinct().filter(u -> !u.equals(me))
                .forEach(u -> members.save(new ConversationMember(c.getId(), u, MemberRole.MEMBER, joinSeq)));
        return ConversationResponse.from(c);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> listMine(UUID me, int limit, Instant cursor) {
        var page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LIMIT)));
        return conversations.findMine(me, cursor == null ? NO_CURSOR : cursor, page).stream()
                .map(ConversationResponse::from).toList();
    }
}
