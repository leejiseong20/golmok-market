package com.golmok.market.domain.chat.event;

import com.golmok.market.domain.chat.dto.ChatMessageResponse;

import java.util.List;

/**
 * 메시지 저장 완료. 응답 DTO 를 트랜잭션 안에서 미리 만들어 담는다.
 * 커밋 뒤에는 영속성 컨텍스트가 닫혀 지연 로딩을 할 수 없기 때문이다.
 *
 * @param participantIds 받을 사람. 보낸 사람도 포함한다(같은 계정의 다른 탭·기기를 맞추기 위해).
 */
public record ChatMessageSentEvent(List<Long> participantIds, ChatMessageResponse message) {
}
