package com.golmok.market.domain.chat;

import com.golmok.market.domain.user.User;
import com.golmok.market.global.entity.BaseCreatedTimeEntity;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지. 수정·삭제 기능은 두지 않는다(대화 기록이 거래 분쟁의 근거가 된다).
 *
 * is_read 는 1:1 방이라 "상대가 읽었는가" 하나면 충분하다.
 * 그룹 채팅이었다면 사용자별 마지막 읽은 메시지 id 를 따로 저장해야 한다.
 */
@Entity
@Getter
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    private ChatMessage(ChatRoom room, User sender, MessageType type, String content) {
        this.room = room;
        this.sender = sender;
        this.type = type;
        this.content = content;
    }

    public static ChatMessage text(ChatRoom room, User sender, String content) {
        if (!room.isActiveParticipant(sender.getId())) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }
        return new ChatMessage(room, sender, MessageType.TEXT, content);
    }

    public static ChatMessage image(ChatRoom room, User sender, String imageUrl) {
        return new ChatMessage(room, sender, MessageType.IMAGE, imageUrl);
    }

    /** 거래 상태 변경 등 시스템이 남기는 안내 메시지 */
    public static ChatMessage system(ChatRoom room, User sender, String content) {
        return new ChatMessage(room, sender, MessageType.SYSTEM, content);
    }

    public void markAsRead() {
        this.read = true;
    }
}
