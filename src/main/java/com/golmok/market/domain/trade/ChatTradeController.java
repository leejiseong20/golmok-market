package com.golmok.market.domain.trade;

import com.golmok.market.domain.chat.dto.ChatRoomResponse;
import com.golmok.market.global.security.AuthUser;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채팅방의 직거래 버튼. 응답은 갱신된 채팅방 정보라 화면이 방을 다시 부르지 않아도 된다.
 * 예약은 방마다 하나라 하위 자원(reservation)으로 두고, 취소는 그 자원의 삭제로 표현한다.
 */
@RestController
@RequestMapping("/api/chat-rooms/{id}")
@RequiredArgsConstructor
public class ChatTradeController {

    private final ChatTradeService chatTradeService;

    @PostMapping("/reservation")
    public ChatRoomResponse reserve(@PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") long id,
                                    @AuthenticationPrincipal AuthUser viewer) {
        return chatTradeService.reserve(id, viewer);
    }

    @DeleteMapping("/reservation")
    public ChatRoomResponse cancel(@PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") long id,
                                   @AuthenticationPrincipal AuthUser viewer) {
        return chatTradeService.cancel(id, viewer);
    }

    @PostMapping("/completion")
    public ChatRoomResponse complete(@PathVariable @Positive(message = "채팅방 ID는 양수여야 합니다.") long id,
                                     @AuthenticationPrincipal AuthUser viewer) {
        return chatTradeService.complete(id, viewer);
    }
}
