package com.golmok.market.domain.chat;

import com.golmok.market.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

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

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    private ChatMessage(ChatRoom room, User sender, MessageType type, String content) {
        this.room = room;
        this.sender = sender;
        this.type = type;
        this.content = content;
    }

    public static ChatMessage text(ChatRoom room, User sender, String content) {
        if (!room.isParticipant(sender.getId())) {
            throw new IllegalArgumentException("이 채팅방의 참여자가 아닙니다.");
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
