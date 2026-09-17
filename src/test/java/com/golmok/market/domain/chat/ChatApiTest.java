package com.golmok.market.domain.chat;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired EntityManager em;

    private User seller;
    private User buyer;
    private Product product;
    private Category category;
    private Region region;
    private String sellerToken;
    private String buyerToken;
    private String otherToken;

    @BeforeEach
    void 준비() {
        seller = userRepository.save(User.builder().email("seller@example.com").password("hash").nickname("판매자").build());
        buyer = userRepository.save(User.builder().email("buyer@example.com").password("hash").nickname("구매자").build());
        User other = userRepository.save(User.builder().email("other@example.com").password("hash").nickname("남").build());
        category = categoryRepository.save(Category.create(null, "가구", null, 1));
        region = regionRepository.save(Region.create("서울특별시", "강남구", "역삼동", 37.5006, 127.0366));
        product = saveProduct("원목 식탁");
        product.addImage("https://example.com/first.jpg");
        em.flush();
        em.clear();
        sellerToken = token(seller);
        buyerToken = token(buyer);
        otherToken = token(other);
    }

    private Product saveProduct(String title) {
        return productRepository.save(Product.builder().seller(seller).category(category).region(region)
                .title(title).description("3년 사용했고 상태가 좋습니다.").price(80000).build());
    }

    private String token(User user) {
        return tokenProvider.createAccessToken(user.getId(), user.getRole());
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private long openRoom(long productId, String token) throws Exception {
        String body = mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", productId), token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.roomId")).longValue();
    }

    private void send(long roomId, String token, String content) throws Exception {
        mockMvc.perform(auth(post("/api/chat-rooms/{id}/messages", roomId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated());
    }

    private void setLastMessageAt(long roomId, LocalDateTime time) {
        em.createQuery("update ChatRoom r set r.lastMessageAt = :time where r.id = :id")
                .setParameter("time", time)
                .setParameter("id", roomId)
                .executeUpdate();
        em.clear();
    }

    private int chatCount() {
        em.clear();
        return productRepository.findById(product.getId()).orElseThrow().getChatCount();
    }

    // ---------- 채팅하기 ----------

    @Test
    void 채팅하기는_201_과_나를_기준으로_한_방_정보를_돌려주고_채팅_수를_올린다() throws Exception {
        mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", product.getId()), buyerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").isNumber())
                .andExpect(jsonPath("$.product.id").value(product.getId()))
                .andExpect(jsonPath("$.product.title").value("원목 식탁"))
                .andExpect(jsonPath("$.product.price").value(80000))
                .andExpect(jsonPath("$.product.thumbnailUrl").value("https://example.com/first.jpg"))
                .andExpect(jsonPath("$.product.status").value("ON_SALE"))
                .andExpect(jsonPath("$.product.deleted").value(false))
                .andExpect(jsonPath("$.opponent.id").value(seller.getId()))
                .andExpect(jsonPath("$.opponent.nickname").value("판매자"))
                .andExpect(jsonPath("$.opponent.mannerTemp").value(36.5))
                .andExpect(jsonPath("$.myRole").value("BUYER"))
                .andExpect(jsonPath("$.opponentLeft").value(false));

        assertThat(chatCount()).isEqualTo(1);
    }

    @Test
    void 같은_상품에_다시_채팅하기를_누르면_같은_방을_200_으로_돌려주고_채팅_수는_그대로다() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);

        mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", product.getId()), buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId));

        assertThat(chatCount()).isEqualTo(1);
    }

    @Test
    void 자기_상품에는_채팅할_수_없다() throws Exception {
        mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", product.getId()), sellerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_CHAT_OWN_PRODUCT"));
    }

    @Test
    void 판매완료_상품에는_새_방을_만들_수_없지만_기존_방은_다시_열린다() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);
        productRepository.findById(product.getId()).orElseThrow().markSold();
        em.flush();

        mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", product.getId()), otherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_CHATTABLE"));
        mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", product.getId()), buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.product.status").value("SOLD"));
    }

    @Test
    void 삭제된_상품이나_없는_상품에는_404() throws Exception {
        productRepository.findById(product.getId()).orElseThrow().softDelete();
        em.flush();

        mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", product.getId()), buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", 999999), buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 비로그인은_채팅_API_를_쓸_수_없다() throws Exception {
        mockMvc.perform(post("/api/products/{id}/chat-rooms", product.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/chat-rooms"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 메시지 ----------

    @Test
    void 메시지를_보내면_201_과_저장된_메시지를_돌려준다() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);

        mockMvc.perform(auth(post("/api/chat-rooms/{id}/messages", roomId), buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"안녕하세요, 아직 판매중인가요?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.senderId").value(buyer.getId()))
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andExpect(jsonPath("$.content").value("안녕하세요, 아직 판매중인가요?"))
                .andExpect(jsonPath("$.read").value(false))
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    void 빈_메시지나_1000자를_넘는_메시지는_400() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);

        mockMvc.perform(auth(post("/api/chat-rooms/{id}/messages", roomId), buyerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("content"));
        mockMvc.perform(auth(post("/api/chat-rooms/{id}/messages", roomId), buyerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + "가".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 메시지는_최신순으로_내려주고_커서로_이전_메시지를_이어_불러온다() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);
        send(roomId, buyerToken, "첫 번째");
        send(roomId, sellerToken, "두 번째");
        send(roomId, buyerToken, "세 번째");

        String firstPage = mockMvc.perform(auth(get("/api/chat-rooms/{id}/messages", roomId), sellerToken)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].content").value("세 번째"))
                .andExpect(jsonPath("$.content[1].content").value("두 번째"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(auth(get("/api/chat-rooms/{id}/messages", roomId), sellerToken)
                        .param("size", "2")
                        .param("cursor", (String) JsonPath.read(firstPage, "$.nextCursor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].content").value("첫 번째"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    void 참여자가_아니면_방_조회_메시지_조회_전송_읽음_나가기_모두_404() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);

        mockMvc.perform(auth(get("/api/chat-rooms/{id}", roomId), otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
        mockMvc.perform(auth(get("/api/chat-rooms/{id}/messages", roomId), otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(post("/api/chat-rooms/{id}/messages", roomId), otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"끼어들기\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(patch("/api/chat-rooms/{id}/read", roomId), otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(delete("/api/chat-rooms/{id}", roomId), otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(get("/api/chat-rooms/{id}", 999999), buyerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
    }

    @Test
    void 판매자가_방을_조회하면_상대는_구매자이고_역할은_SELLER_다() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);

        mockMvc.perform(auth(get("/api/chat-rooms/{id}", roomId), sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opponent.nickname").value("구매자"))
                .andExpect(jsonPath("$.myRole").value("SELLER"));
    }

    // ---------- 목록 · 읽음 ----------

    @Test
    void 메시지가_없는_방은_목록에_나오지_않는다() throws Exception {
        openRoom(product.getId(), buyerToken);

        mockMvc.perform(auth(get("/api/chat-rooms"), sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 목록에는_마지막_메시지와_내_기준_안_읽은_수가_나온다() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);
        send(roomId, buyerToken, "안녕하세요");
        send(roomId, buyerToken, "네고 가능할까요?");

        mockMvc.perform(auth(get("/api/chat-rooms"), sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].roomId").value(roomId))
                .andExpect(jsonPath("$.content[0].opponent.nickname").value("구매자"))
                .andExpect(jsonPath("$.content[0].product.title").value("원목 식탁"))
                .andExpect(jsonPath("$.content[0].lastMessage").value("네고 가능할까요?"))
                .andExpect(jsonPath("$.content[0].lastMessageAt").isString())
                .andExpect(jsonPath("$.content[0].unreadCount").value(2));
        mockMvc.perform(auth(get("/api/chat-rooms"), buyerToken))
                .andExpect(jsonPath("$.content[0].opponent.nickname").value("판매자"))
                .andExpect(jsonPath("$.content[0].unreadCount").value(0));
    }

    @Test
    void 목록의_마지막_메시지는_200자로_자른다() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);
        send(roomId, buyerToken, "가".repeat(250));

        mockMvc.perform(auth(get("/api/chat-rooms"), sellerToken))
                .andExpect(jsonPath("$.content[0].lastMessage").value("가".repeat(200)));
    }

    @Test
    void 읽음_처리하면_안_읽은_수가_0_이_되고_상대에게는_읽음으로_보인다() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);
        send(roomId, buyerToken, "안녕하세요");

        mockMvc.perform(auth(patch("/api/chat-rooms/{id}/read", roomId), sellerToken))
                .andExpect(status().isNoContent());
        // 읽을 것이 없어도 성공한다.
        mockMvc.perform(auth(patch("/api/chat-rooms/{id}/read", roomId), sellerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(auth(get("/api/chat-rooms"), sellerToken))
                .andExpect(jsonPath("$.content[0].unreadCount").value(0));
        mockMvc.perform(auth(get("/api/chat-rooms/{id}/messages", roomId), buyerToken))
                .andExpect(jsonPath("$.content[0].read").value(true));
    }

    @Test
    void 목록은_최근_메시지_순이고_커서로_이어_불러온다() throws Exception {
        Product another = saveProduct("의자");
        em.flush();
        long olderRoom = openRoom(product.getId(), buyerToken);
        long newerRoom = openRoom(another.getId(), buyerToken);
        send(newerRoom, buyerToken, "의자 문의");
        send(olderRoom, buyerToken, "식탁 문의");
        // 두 전송이 같은 시각으로 찍히면 방 id 로 정렬돼 테스트가 시계 해상도에 좌우된다. 시각을 직접 정한다.
        setLastMessageAt(newerRoom, LocalDateTime.of(2026, 9, 17, 10, 0, 0));
        setLastMessageAt(olderRoom, LocalDateTime.of(2026, 9, 17, 10, 0, 1));

        String firstPage = mockMvc.perform(auth(get("/api/chat-rooms"), sellerToken).param("size", "1"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].roomId").value(olderRoom))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(auth(get("/api/chat-rooms"), sellerToken)
                        .param("size", "1")
                        .param("cursor", (String) JsonPath.read(firstPage, "$.nextCursor")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].roomId").value(newerRoom))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // ---------- 나가기 ----------

    @Test
    void 나가면_내_목록에서_사라지고_방에_접근할_수_없지만_상대는_계속_본다() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);
        send(roomId, buyerToken, "안녕하세요");

        mockMvc.perform(auth(delete("/api/chat-rooms/{id}", roomId), sellerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(auth(get("/api/chat-rooms"), sellerToken))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(auth(get("/api/chat-rooms/{id}/messages", roomId), sellerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(delete("/api/chat-rooms/{id}", roomId), sellerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(get("/api/chat-rooms/{id}", roomId), buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opponentLeft").value(true));
    }

    @Test
    void 나간_사람에게_상대가_메시지를_보내면_방이_다시_나타나고_새_메시지만_안_읽은_수에_잡힌다() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);
        send(roomId, buyerToken, "안녕하세요");
        mockMvc.perform(auth(delete("/api/chat-rooms/{id}", roomId), sellerToken));

        send(roomId, buyerToken, "혹시 나가셨나요?");

        mockMvc.perform(auth(get("/api/chat-rooms"), sellerToken))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].lastMessage").value("혹시 나가셨나요?"))
                .andExpect(jsonPath("$.content[0].unreadCount").value(1));
    }

    @Test
    void 나간_구매자가_채팅하기를_다시_누르면_같은_방으로_돌아온다() throws Exception {
        long roomId = openRoom(product.getId(), buyerToken);
        send(roomId, sellerToken, "네 판매중입니다");
        mockMvc.perform(auth(delete("/api/chat-rooms/{id}", roomId), buyerToken));

        mockMvc.perform(auth(post("/api/products/{id}/chat-rooms", product.getId()), buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId));
        mockMvc.perform(auth(get("/api/chat-rooms"), buyerToken))
                .andExpect(jsonPath("$.content", hasSize(1)));
        assertThat(chatCount()).isEqualTo(1);
    }
}
