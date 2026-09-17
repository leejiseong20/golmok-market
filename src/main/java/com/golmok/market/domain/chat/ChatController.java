package com.golmok.market.domain.chat;

import com.golmok.market.domain.chat.dto.ChatMessageResponse;
import com.golmok.market.domain.chat.dto.ChatMessageSendRequest;
import com.golmok.market.domain.chat.dto.ChatRoomResponse;
import com.golmok.market.domain.chat.dto.ChatRoomSummaryResponse;
import com.golmok.market.global.pagination.CursorResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 모든 채팅 API 는 인증이 필요하다. SecurityConfig 의 anyRequest().authenticated() 에 해당한다. */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 채팅하기. 새로 만들면 201, 이미 있던 방이면 200. 응답 본문은 같다. */
    @PostMapping("/api/products/{id}/chat-rooms")
    public ResponseEntity<ChatRoomResponse> open(@PathVariable @Positive(message = "상품 ID는 양수여야 합니다.") long id,
                                                 @AuthenticationPrincipal AuthUser viewer) {
        ChatService.OpenResult result = chatService.open(id, viewer);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.room());
    }

    @GetMapping("/api/chat-rooms")
    public CursorResponse<ChatRoomSummaryResponse> findMyRooms(@AuthenticationPrincipal AuthUser viewer,
                                                               @RequestParam(required = false) String cursor,
                                                               @RequestParam(required = false) Integer size) {
        return chatService.findMyRooms(viewer, cursor, size);
    }

    @GetMapping("/api/chat-rooms/{id}")
    public ChatRoomResponse findRoom(@PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") long id,
                                     @AuthenticationPrincipal AuthUser viewer) {
        return chatService.findRoom(id, viewer);
    }

    @GetMapping("/api/chat-rooms/{id}/messages")
    public CursorResponse<ChatMessageResponse> findMessages(
            @PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") long id,
            @AuthenticationPrincipal AuthUser viewer,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return chatService.findMessages(id, viewer, cursor, size);
    }

    @PostMapping("/api/chat-rooms/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse send(@PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") long id,
                                    @AuthenticationPrincipal AuthUser viewer,
                                    @Valid @RequestBody ChatMessageSendRequest request) {
        return chatService.send(id, viewer, request);
    }

    @PatchMapping("/api/chat-rooms/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") long id,
                                           @AuthenticationPrincipal AuthUser viewer) {
        chatService.markAsRead(id, viewer);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/chat-rooms/{id}")
    public ResponseEntity<Void> leave(@PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") long id,
                                      @AuthenticationPrincipal AuthUser viewer) {
        chatService.leave(id, viewer);
        return ResponseEntity.noContent().build();
    }
}
