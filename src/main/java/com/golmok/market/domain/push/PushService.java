package com.golmok.market.domain.push;

import com.golmok.market.domain.push.dto.PushPublicKeyResponse;
import com.golmok.market.domain.push.dto.PushSubscribeRequest;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import com.golmok.market.global.security.AuthUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 웹 푸시 구독 관리와 발송.
 *
 * 발송은 요청 스레드가 아니라 PushRelay 의 전용 실행기에서 한다. 푸시 서비스가 느려도 채팅 전송이 기다리지 않는다.
 * 푸시는 부가 기능이다 — 실패해도 원래 동작(채팅·알림 저장)은 이미 끝나 있고 되돌리지 않는다.
 */
@Slf4j
@Service
@EnableConfigurationProperties(PushProperties.class)
public class PushService {

    /** 기기가 꺼져 있으면 푸시 서비스가 기다려 주는 시간. 하루 지난 채팅 알림은 의미가 적다. */
    static final Duration TTL = Duration.ofHours(24);
    private static final int AUTH_LENGTH = 16;

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PushGateway gateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    /** 키가 없거나 틀리면 null — 푸시가 꺼진 상태다. */
    private final Vapid vapid;

    public PushService(PushSubscriptionRepository subscriptionRepository, UserRepository userRepository,
                       PushGateway gateway, ObjectMapper objectMapper, Clock clock, PushProperties properties) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.vapid = loadVapid(properties);
    }

    /**
     * 키가 없으면 푸시를 끈다. 키가 틀려도(형식 오류·짝 불일치) **앱은 띄우고 푸시만 끈다.**
     * 서버가 한 대라, 키 하나 잘못 넣었다고 기동이 멈추면 서비스 전체가 내려간다.
     * 대신 오류 로그를 남기고, 공개키 API 가 enabled=false 를 돌려줘 밖에서도 알 수 있다.
     */
    private static Vapid loadVapid(PushProperties properties) {
        if (!properties.configured()) {
            log.info("VAPID 키가 없어 웹 푸시를 끈다");
            return null;
        }
        try {
            return Vapid.of(properties.publicKey(), properties.privateKey(), properties.subject());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("VAPID 키가 올바르지 않아 웹 푸시를 끈다. deploy/.env 의 VAPID_* 를 확인할 것", e);
            return null;
        }
    }

    public boolean enabled() {
        return vapid != null;
    }

    public PushPublicKeyResponse publicKey() {
        return new PushPublicKeyResponse(enabled(), enabled() ? vapid.publicKey() : null);
    }

    /**
     * 구독 저장. 같은 기기(endpoint)면 새로 만들지 않고 주인과 키를 바꾼다.
     * 같은 브라우저에서 다른 계정으로 로그인해 구독하면, 이전 사람의 알림이 이 기기로 오지 않게 된다.
     */
    @Transactional
    public void subscribe(AuthUser viewer, PushSubscribeRequest request) {
        if (!enabled()) {
            throw new BusinessException(ErrorCode.PUSH_DISABLED);
        }
        String endpoint = request.endpoint().trim();
        if (!PushEndpoints.allowed(endpoint)) {
            throw new BusinessException(ErrorCode.INVALID_PUSH_SUBSCRIPTION, "지원하지 않는 푸시 서비스 주소입니다.");
        }
        String p256dh = request.keys().p256dh().trim();
        String auth = request.keys().auth().trim();
        validateKeys(p256dh, auth);

        var user = userRepository.getReferenceById(viewer.id());
        subscriptionRepository.findByEndpoint(endpoint).ifPresentOrElse(
                existing -> existing.renew(user, p256dh, auth),
                () -> subscriptionRepository.save(PushSubscription.of(user, endpoint, p256dh, auth)));
    }

    /** 이 기기의 구독을 지운다(알림 끄기·로그아웃). 없거나 남의 구독이면 아무 일도 하지 않는다. */
    @Transactional
    public void unsubscribe(AuthUser viewer, String endpoint) {
        subscriptionRepository.deleteMine(endpoint.trim(), viewer.id());
    }

    /**
     * 그 사람의 모든 기기로 보낸다. 트랜잭션 없이 돈다 — 푸시 서비스를 기다리는 동안 DB 연결을 붙잡지 않는다.
     * 구독이 없어진 기기(GONE)는 그때 지운다.
     */
    public void send(long userId, PushMessage message) {
        if (!enabled()) {
            return;
        }
        List<PushSubscription> subscriptions = subscriptionRepository.findAllByUserId(userId);
        if (subscriptions.isEmpty()) {
            return;
        }
        byte[] payload = payload(message);
        for (PushSubscription subscription : subscriptions) {
            byte[] body = WebPushEncryptor.encrypt(payload,
                    decode(subscription.getP256dh()), decode(subscription.getAuth()));
            PushGateway.Result result = gateway.send(subscription.getEndpoint(), body,
                    vapid.authorization(subscription.getEndpoint(), clock.instant()), TTL, message.urgency());
            if (result == PushGateway.Result.GONE) {
                subscriptionRepository.deleteById(subscription.getId());
            }
        }
    }

    /** 서비스 워커(sw.js)가 읽는 모양. 필드를 바꾸면 sw.js 도 함께 바꾼다. */
    private byte[] payload(PushMessage message) {
        Map<String, String> json = new LinkedHashMap<>();
        json.put("title", message.title());
        json.put("body", message.body());
        json.put("url", message.url());
        json.put("tag", message.tag());
        return objectMapper.writeValueAsString(json).getBytes(StandardCharsets.UTF_8);
    }

    /** 브라우저가 준 키가 형식에 맞는지 저장 전에 본다. 틀린 키로 저장하면 보낼 때마다 암호화가 실패한다. */
    private static void validateKeys(String p256dh, String auth) {
        try {
            P256.decodePublic(decode(p256dh));
            if (decode(auth).length != AUTH_LENGTH) {
                throw new IllegalArgumentException("auth 길이");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_PUSH_SUBSCRIPTION, "구독 키가 올바르지 않습니다.");
        }
    }

    private static byte[] decode(String base64url) {
        return Base64.getUrlDecoder().decode(base64url);
    }
}
