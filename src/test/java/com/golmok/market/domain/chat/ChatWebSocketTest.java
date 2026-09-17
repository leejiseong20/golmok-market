package com.golmok.market.domain.chat;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.chat.dto.ChatMessageSendRequest;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.AuthUser;
import com.golmok.market.global.security.JwtProperties;
import com.golmok.market.global.security.JwtTokenProvider;
import com.golmok.market.global.security.StompAuthChannelInterceptor;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 포트로 서버를 띄워 STOMP 클라이언트로 연결한다.
 *
 * MockMvc 로는 WebSocket 을 검증할 수 없고, 롤백 트랜잭션 안의 데이터는 커밋 후 전달(AFTER_COMMIT)도
 * 일어나지 않으므로 준비 데이터를 커밋한다. 전용 H2 DB 를 컨텍스트 종료 시 폐기한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:chat-websocket;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatWebSocketTest {

    private static final long TIMEOUT_SECONDS = 5;

    @LocalServerPort int port;
    @Autowired ChatService chatService;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired JwtProperties jwtProperties;
    @Autowired SimpUserRegistry userRegistry;

    private final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    private final List<StompSession> sessions = new ArrayList<>();

    private User seller;
    private User buyer;
    private User outsider;
    private long roomId;

    @BeforeEach
    void 준비() {
        // 테스트끼리 같은 DB 를 쓰므로 이메일·닉네임이 겹치지 않게 한다.
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        seller = saveUser("seller", suffix);
        buyer = saveUser("buyer", suffix);
        outsider = saveUser("out", suffix);
        Category category = categoryRepository.save(Category.create(null, "가구" + suffix, null, 1));
        Region region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동" + suffix, 37.5, 127));
        Product product = productRepository.save(Product.builder().seller(seller).category(category).region(region)
                .title("원목 식탁").description("실시간 채팅을 확인할 상품입니다.").price(80000).build());
        roomId = chatService.open(product.getId(), auth(buyer)).room().roomId();
    }

    @AfterEach
    void 연결_정리() {
        for (StompSession session : sessions) {
            try {
                session.disconnect();
            } catch (RuntimeException ignored) {
                // 서버가 ERROR 프레임으로 이미 끊은 세션은 isConnected() 가 잠깐 true 로 남아 있어도 전송이 실패한다.
                // 정리 단계의 실패로 테스트 결과를 바꾸지 않는다.
            }
        }
    }

    private User saveUser(String prefix, String suffix) {
        return userRepository.save(User.builder().email(prefix + suffix + "@example.com").password("hash")
                .nickname(prefix + suffix).build());
    }

    private static AuthUser auth(User user) {
        return new AuthUser(user.getId(), user.getRole());
    }

    private String token(User user) {
        return tokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    private String url() {
        return "ws://localhost:" + port + "/api/ws";
    }

    // ---------- 연결 도우미 ----------

    /** 연결 결과와 서버가 보낸 ERROR 프레임을 함께 들고 있는다. */
    private record Connection(CompletableFuture<StompSession> session, CompletableFuture<StompHeaders> error) {
    }

    private Connection connect(String authorization, WebSocketHttpHeaders handshakeHeaders) {
        CompletableFuture<StompHeaders> error = new CompletableFuture<>();
        StompHeaders connectHeaders = new StompHeaders();
        if (authorization != null) {
            connectHeaders.add("Authorization", authorization);
        }
        CompletableFuture<StompSession> session = stompClient.connectAsync(url(), handshakeHeaders, connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        // 세션 핸들러로 오는 프레임은 ERROR 뿐이다(구독 메시지는 구독 핸들러로 간다).
                        error.complete(headers);
                    }
                });
        session.thenAccept(sessions::add);
        return new Connection(session, error);
    }

    private StompSession connectAs(User user) throws Exception {
        return connect("Bearer " + token(user), new WebSocketHttpHeaders()).session().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** 개인 큐를 구독하고, 서버에 구독이 등록될 때까지 기다린다(구독 전에 보낸 이벤트는 받을 수 없다). */
    private BlockingQueue<String> subscribe(StompSession session, User user) throws InterruptedException {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe(StompAuthChannelInterceptor.CHAT_SUBSCRIPTION, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        awaitSubscribed(user);
        return received;
    }

    private void awaitSubscribed(User user) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            SimpUser simpUser = userRegistry.getUser(String.valueOf(user.getId()));
            if (simpUser != null && simpUser.getSessions().stream().anyMatch(s -> !s.getSubscriptions().isEmpty())) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("구독이 서버에 등록되지 않았다");
    }

    private void send(User sender, String content) {
        chatService.send(roomId, auth(sender), new ChatMessageSendRequest(content));
    }

    private static String errorCode(Connection connection) throws Exception {
        return connection.error().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getFirst("message");
    }

    // ---------- 인증 ----------

    @Test
    void 토큰_없이_연결하면_UNAUTHORIZED_로_거부된다() throws Exception {
        Connection connection = connect(null, new WebSocketHttpHeaders());

        assertThat(errorCode(connection)).isEqualTo("UNAUTHORIZED");
        assertThatThrownBy(() -> connection.session().get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void 위조_토큰은_INVALID_TOKEN_만료_토큰은_EXPIRED_TOKEN_으로_거부된다() throws Exception {
        assertThat(errorCode(connect("Bearer not-a-jwt", new WebSocketHttpHeaders()))).isEqualTo("INVALID_TOKEN");

        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneId.of("Asia/Seoul"));
        String expired = new JwtTokenProvider(jwtProperties, past).createAccessToken(buyer.getId(), buyer.getRole());
        // 프론트는 이 코드를 보고 토큰을 재발급한 뒤 다시 연결한다.
        assertThat(errorCode(connect("Bearer " + expired, new WebSocketHttpHeaders()))).isEqualTo("EXPIRED_TOKEN");
    }

    @Test
    void 허용하지_않은_출처의_연결_요청은_거부된다() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://evil.example.com");

        assertThatThrownBy(() -> connect("Bearer " + token(buyer), headers).session().get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    // ---------- 인가 ----------

    @Test
    void 개인_큐가_아닌_목적지는_구독할_수_없다() throws Exception {
        CompletableFuture<StompHeaders> error = new CompletableFuture<>();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token(outsider));
        StompSession session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        error.complete(headers);
                    }
                }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);

        // 남의 개인 큐 실제 이름을 추측해 직접 구독하는 시도
        session.subscribe("/queue/chat-user" + buyer.getId(), new StompSessionHandlerAdapter() {
        });

        assertThat(error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getFirst("message")).isEqualTo("FORBIDDEN");
    }

    @Test
    void SEND_프레임은_거부된다() throws Exception {
        CompletableFuture<StompHeaders> error = new CompletableFuture<>();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token(outsider));
        StompSession session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        error.complete(headers);
                    }
                }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);

        session.send("/queue/chat-user" + buyer.getId(), "가짜 메시지".getBytes(StandardCharsets.UTF_8));

        assertThat(error.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getFirst("message")).isEqualTo("FORBIDDEN");
    }

    // ---------- 전달 ----------

    @Test
    void 메시지를_보내면_두_참여자가_실시간으로_받고_제3자는_받지_못한다() throws Exception {
        BlockingQueue<String> sellerInbox = subscribe(connectAs(seller), seller);
        BlockingQueue<String> buyerInbox = subscribe(connectAs(buyer), buyer);
        BlockingQueue<String> outsiderInbox = subscribe(connectAs(outsider), outsider);

        send(buyer, "안녕하세요, 판매중인가요?");

        String event = sellerInbox.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("MESSAGE");
        assertThat(((Number) JsonPath.read(event, "$.roomId")).longValue()).isEqualTo(roomId);
        assertThat(((Number) JsonPath.read(event, "$.message.senderId")).longValue()).isEqualTo(buyer.getId());
        assertThat((String) JsonPath.read(event, "$.message.content")).isEqualTo("안녕하세요, 판매중인가요?");
        assertThat((String) JsonPath.read(event, "$.message.createdAt")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        assertThat(event).doesNotContain("readerId"); // 쓰지 않는 필드는 내려가지 않는다

        // 보낸 사람도 받는다(같은 계정의 다른 탭을 맞추기 위해).
        assertThat(buyerInbox.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        // 제3자에게는 오지 않는다. 참여자에게 이미 도착했으므로 짧게만 더 기다린다.
        assertThat(outsiderInbox.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void 같은_사용자의_여러_연결에_모두_전달된다() throws Exception {
        BlockingQueue<String> firstTab = subscribe(connectAs(seller), seller);
        BlockingQueue<String> secondTab = new LinkedBlockingQueue<>();
        StompSession second = connectAs(seller);
        second.subscribe(StompAuthChannelInterceptor.CHAT_SUBSCRIPTION, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                secondTab.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        awaitSubscriptionCount(seller, 2);

        send(buyer, "두 탭 모두 받아야 합니다");

        assertThat(firstTab.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
        assertThat(secondTab.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
    }

    private void awaitSubscriptionCount(User user, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            SimpUser simpUser = userRegistry.getUser(String.valueOf(user.getId()));
            if (simpUser != null && simpUser.getSessions().stream().filter(s -> !s.getSubscriptions().isEmpty()).count() >= expected) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("구독 " + expected + "개가 등록되지 않았다");
    }

    @Test
    void 읽음_처리하면_상대가_READ_이벤트를_받고_읽을_것이_없으면_보내지_않는다() throws Exception {
        send(buyer, "안녕하세요");
        BlockingQueue<String> buyerInbox = subscribe(connectAs(buyer), buyer);

        chatService.markAsRead(roomId, auth(seller));

        String event = buyerInbox.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("READ");
        assertThat(((Number) JsonPath.read(event, "$.roomId")).longValue()).isEqualTo(roomId);
        assertThat(((Number) JsonPath.read(event, "$.readerId")).longValue()).isEqualTo(seller.getId());
        assertThat(event).doesNotContain("\"message\"");

        // 이미 모두 읽었으므로 다시 불러도 이벤트가 없다.
        chatService.markAsRead(roomId, auth(seller));
        assertThat(buyerInbox.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }
}
