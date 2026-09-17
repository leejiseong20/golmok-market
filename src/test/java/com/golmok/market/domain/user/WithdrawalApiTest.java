package com.golmok.market.domain.user;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.notification.Notification;
import com.golmok.market.domain.notification.NotificationRepository;
import com.golmok.market.domain.notification.NotificationType;
import com.golmok.market.domain.product.FavoriteRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.global.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원 탈퇴. "나"는 판매 상품 2개·찜 1개·동네·알림·refresh token 을 가진 판매자이고,
 * 이웃은 내 상품에 채팅을 건 구매자이자 내가 찜한 상품의 판매자다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WithdrawalApiTest {

    private static final String PASSWORD = "Golmok123!";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired FavoriteRepository favoriteRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRegionRepository userRegionRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired EntityManager em;

    private User me;
    private User neighbor;
    private Category category;
    private Region region;
    private Product myTable;
    private Product myChair;
    private Product neighborLamp;
    private String myToken;
    private String neighborToken;
    private long roomId;

    @BeforeEach
    void 준비() throws Exception {
        me = userRepository.save(User.builder().email("me@example.com")
                .password(passwordEncoder.encode(PASSWORD)).nickname("나").build());
        neighbor = userRepository.save(User.builder().email("neighbor@example.com")
                .password(passwordEncoder.encode(PASSWORD)).nickname("이웃").build());
        category = categoryRepository.save(Category.create(null, "가구", null, 1));
        region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5006, 127.0366));
        myTable = product(me, "원목 식탁");
        myChair = product(me, "의자");
        neighborLamp = product(neighbor, "스탠드");
        userRegionRepository.save(UserRegion.verify(me, region, true));
        notificationRepository.save(Notification.of(me, NotificationType.TRADE, "알림", null, "/chat-rooms/1"));
        refreshTokenRepository.save(RefreshToken.issue(me, "hash-of-token", "test", LocalDateTime.now().plusDays(1)));
        em.flush();
        em.clear();
        myToken = tokenProvider.createAccessToken(me.getId(), me.getRole());
        neighborToken = tokenProvider.createAccessToken(neighbor.getId(), neighbor.getRole());

        mockMvc.perform(auth(post("/api/products/{id}/favorite", neighborLamp.getId()), myToken))
                .andExpect(status().isOk());
        roomId = openRoom(myTable, neighborToken);
        send(roomId, neighborToken, "안녕하세요").andExpect(status().isCreated());
    }

    private Product product(User seller, String title) {
        return productRepository.save(Product.builder().seller(seller).category(category).region(region)
                .title(title).description("상태 좋습니다.").price(10000).build());
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private long openRoom(Product product, String token) throws Exception {
        String body = mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", product.getId()), token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.roomId")).longValue();
    }

    private ResultActions send(long room, String token, String content) throws Exception {
        return mockMvc.perform(auth(post("/api/chat-rooms/{id}/messages", room), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"));
    }

    /**
     * 테스트 메서드 트랜잭션을 MockMvc 요청들이 함께 써서, 앞선 요청이 읽은 채팅방 엔티티가 탈퇴의 벌크 UPDATE 뒤에도 남는다.
     * 운영에서는 요청마다 영속성 컨텍스트가 새로 생기므로, 탈퇴 뒤 컨텍스트를 비워 같은 조건을 만든다.
     */
    private ResultActions withdraw(String token, String password) throws Exception {
        ResultActions result = mockMvc.perform(auth(delete("/api/users/me"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
        em.flush();
        em.clear();
        return result;
    }

    private User reload(User user) {
        em.flush();
        em.clear();
        return userRepository.findById(user.getId()).orElseThrow();
    }

    // ---------- 성공 ----------

    @Test
    void 탈퇴하면_회원을_익명화하고_상품_찜_알림_동네_토큰을_정리한다() throws Exception {
        withdraw(myToken, PASSWORD).andExpect(status().isNoContent());

        User withdrawn = reload(me);
        assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(withdrawn.getDeletedAt()).isNotNull();
        assertThat(withdrawn.getEmail()).isEqualTo("withdrawn-" + me.getId() + "@deleted.invalid");
        assertThat(withdrawn.getNickname()).isEqualTo(User.WITHDRAWN_NICKNAME_PREFIX + me.getId());
        assertThat(withdrawn.getProfileImageUrl()).isNull();

        assertThat(productRepository.findById(myTable.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(productRepository.findById(myChair.getId()).orElseThrow().isDeleted()).isTrue();
        // 내가 한 찜은 지우고 상대 상품의 찜 수도 되돌린다.
        assertThat(productRepository.findById(neighborLamp.getId()).orElseThrow().getFavoriteCount()).isZero();
        assertThat(favoriteRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
        assertThat(userRegionRepository.count()).isZero();
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void 탈퇴한_회원은_원래_이메일로_로그인할_수_없고_프로필_후기_내_정보가_404_다() throws Exception {
        withdraw(myToken, PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"me@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        mockMvc.perform(get("/api/users/{id}", me.getId()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mockMvc.perform(get("/api/users/{id}/reviews", me.getId()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        // 남은 access token(최대 30분)으로 내 정보를 보거나 익명화된 프로필을 되돌리지 못한다.
        mockMvc.perform(auth(get("/api/users/me"), myToken)).andExpect(status().isNotFound());
        mockMvc.perform(auth(patch("/api/users/me"), myToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"되살리기\",\"profileImageUrl\":null}"))
                .andExpect(status().isNotFound());
        withdraw(myToken, PASSWORD)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 탈퇴하면_채팅방에서_나가고_상대는_대화를_보지만_메시지를_보낼_수_없다() throws Exception {
        withdraw(myToken, PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(auth(get("/api/chat-rooms/{id}", roomId), neighborToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opponentLeft").value(true))
                .andExpect(jsonPath("$.opponent.withdrawn").value(true))
                .andExpect(jsonPath("$.opponent.nickname").value(User.WITHDRAWN_NICKNAME_PREFIX + me.getId()))
                .andExpect(jsonPath("$.product.deleted").value(true));
        mockMvc.perform(auth(get("/api/chat-rooms/{id}/messages", roomId), neighborToken))
                .andExpect(jsonPath("$.content[0].content").value("안녕하세요"));
        send(roomId, neighborToken, "계세요?")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHAT_OPPONENT_WITHDRAWN"));
        mockMvc.perform(auth(get("/api/chat-rooms"), neighborToken))
                .andExpect(jsonPath("$.content[0].opponent.withdrawn").value(true));
    }

    @Test
    void 탈퇴한_구매자에게는_판매자가_예약할_수_없다() throws Exception {
        long lampRoom = openRoom(neighborLamp, myToken);
        send(lampRoom, myToken, "살게요").andExpect(status().isCreated());
        withdraw(myToken, PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(auth(get("/api/chat-rooms/{id}", lampRoom), neighborToken))
                .andExpect(jsonPath("$.tradeActions.reserve").value(false));
        mockMvc.perform(auth(post("/api/chat-rooms/{id}/reservation", lampRoom), neighborToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHAT_OPPONENT_WITHDRAWN"));
    }

    // ---------- 막는 경우 ----------

    @Test
    void 비밀번호가_틀리면_400_이고_아무것도_바뀌지_않는다() throws Exception {
        withdraw(myToken, "Wrong123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        assertThat(reload(me).isWithdrawn()).isFalse();
        assertThat(productRepository.findById(myTable.getId()).orElseThrow().isDeleted()).isFalse();
        assertThat(favoriteRepository.count()).isEqualTo(1);
    }

    @Test
    void 판매자로서_진행_중인_거래가_있으면_409() throws Exception {
        mockMvc.perform(auth(post("/api/chat-rooms/{id}/reservation", roomId), myToken)).andExpect(status().isOk());

        withdraw(myToken, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRADE_IN_PROGRESS"));
        assertThat(reload(me).isWithdrawn()).isFalse();
    }

    @Test
    void 구매자로서_진행_중인_거래가_있어도_409() throws Exception {
        long lampRoom = openRoom(neighborLamp, myToken);
        mockMvc.perform(auth(post("/api/chat-rooms/{id}/reservation", lampRoom), neighborToken))
                .andExpect(status().isOk());

        withdraw(myToken, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRADE_IN_PROGRESS"));
    }

    @Test
    void 비밀번호가_없으면_400_비로그인은_401() throws Exception {
        mockMvc.perform(auth(delete("/api/users/me"), myToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
