package com.golmok.market.domain.block;

import com.golmok.market.domain.category.Category;
import com.golmok.market.domain.category.CategoryRepository;
import com.golmok.market.domain.chat.ChatMessage;
import com.golmok.market.domain.chat.ChatMessageRepository;
import com.golmok.market.domain.chat.ChatRoom;
import com.golmok.market.domain.chat.ChatRoomRepository;
import com.golmok.market.domain.product.Product;
import com.golmok.market.domain.product.ProductRepository;
import com.golmok.market.domain.region.Region;
import com.golmok.market.domain.region.RegionRepository;
import com.golmok.market.domain.user.User;
import com.golmok.market.domain.user.UserRegion;
import com.golmok.market.domain.user.UserRegionRepository;
import com.golmok.market.domain.user.UserRepository;
import com.golmok.market.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 차단과 그 효과.
 *
 * 차단 자체보다 "차단하면 무엇이 달라지는가"가 중요하다. 목록·채팅·뱃지를 각각 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BlockApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserRegionRepository userRegions;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired RegionRepository regions;
    @Autowired ChatRoomRepository chatRooms;
    @Autowired ChatMessageRepository chatMessages;
    @Autowired BlockRepository blocks;
    @Autowired JwtTokenProvider tokens;
    @Autowired EntityManager em;

    User me, other;
    Region region;
    Product otherProduct;
    String myToken, otherToken;

    @BeforeEach
    void 준비() {
        me = users.save(User.builder().email("me@test.com").password("hash").nickname("차단하는사람").build());
        other = users.save(User.builder().email("other@test.com").password("hash").nickname("차단당하는사람").build());
        myToken = tokens.createAccessToken(me.getId(), me.getRole());
        otherToken = tokens.createAccessToken(other.getId(), other.getRole());
        Category category = categories.save(Category.create(null, "차단카테고리", null, 1));
        region = regions.save(Region.create("서울", "강남", "역삼동", 37.5, 127.0));
        userRegions.save(UserRegion.verify(me, region, true));
        otherProduct = products.save(Product.builder().seller(other).category(category).region(region)
                .title("차단할 사람의 상품").description("차단 효과를 확인하는 상품입니다.").price(10000).build());
    }

    private void block(String token, long targetId) throws Exception {
        mvc.perform(post("/api/users/{id}/block", targetId).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    // ---------- 차단 자체 ----------

    @Test
    void 차단하고_해제할_수_있다() throws Exception {
        block(myToken, other.getId());
        assertThat(blocks.existsByBlockerIdAndBlockedId(me.getId(), other.getId())).isTrue();

        mvc.perform(get("/api/users/me/blocks").header("Authorization", "Bearer " + myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId").value(other.getId()))
                .andExpect(jsonPath("$.content[0].nickname").value("차단당하는사람"));

        mvc.perform(delete("/api/users/{id}/block", other.getId()).header("Authorization", "Bearer " + myToken))
                .andExpect(status().isNoContent());
        assertThat(blocks.existsByBlockerIdAndBlockedId(me.getId(), other.getId())).isFalse();
    }

    @Test
    void 같은_사람을_두_번_차단할_수_없다() throws Exception {
        block(myToken, other.getId());
        mvc.perform(post("/api/users/{id}/block", other.getId()).header("Authorization", "Bearer " + myToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_BLOCKED"));
    }

    @Test
    void 자기_자신은_차단할_수_없다() throws Exception {
        mvc.perform(post("/api/users/{id}/block", me.getId()).header("Authorization", "Bearer " + myToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_BLOCK_SELF"));
    }

    @Test
    void 없는_사용자는_차단할_수_없다() throws Exception {
        mvc.perform(post("/api/users/{id}/block", 999999).header("Authorization", "Bearer " + myToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    /** 차단하지 않은 사람을 해제해도 에러가 아니다(멱등). 화면 상태가 어긋나도 한 번 더 눌러 맞춰진다. */
    @Test
    void 차단하지_않은_사람의_해제는_조용히_넘어간다() throws Exception {
        mvc.perform(delete("/api/users/{id}/block", other.getId()).header("Authorization", "Bearer " + myToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void 로그인하지_않으면_차단할_수_없다() throws Exception {
        mvc.perform(post("/api/users/{id}/block", other.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/blocks")).andExpect(status().isUnauthorized());
    }

    /** 화면이 "차단하기"와 "차단 해제" 중 무엇을 보일지 정하려면 프로필에 차단 여부가 있어야 한다. */
    @Test
    void 프로필에_내가_차단했는지가_실린다() throws Exception {
        mvc.perform(get("/api/users/{id}", other.getId()).header("Authorization", "Bearer " + myToken))
                .andExpect(jsonPath("$.blockedByMe").value(false));
        block(myToken, other.getId());
        mvc.perform(get("/api/users/{id}", other.getId()).header("Authorization", "Bearer " + myToken))
                .andExpect(jsonPath("$.blockedByMe").value(true));

        // 차단당한 쪽에는 드러나지 않는다. 비로그인에게도 항상 false 다.
        mvc.perform(get("/api/users/{id}", me.getId()).header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.blockedByMe").value(false));
        mvc.perform(get("/api/users/{id}", other.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedByMe").value(false));
    }

    // ---------- 효과 1: 목록 ----------

    @Test
    void 차단하면_그_사람_상품이_목록에서_빠진다() throws Exception {
        mvc.perform(get("/api/products").param("regionId", String.valueOf(region.getId()))
                        .header("Authorization", "Bearer " + myToken))
                .andExpect(jsonPath("$.content", hasSize(1)));

        block(myToken, other.getId());

        mvc.perform(get("/api/products").param("regionId", String.valueOf(region.getId()))
                        .header("Authorization", "Bearer " + myToken))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    /** 목록에서만 뺀다. 주소로 상세에 들어오면 상품은 보인다(차단은 숨김이 아니라 관계를 끊는 일이다). */
    @Test
    void 차단해도_상세는_보인다() throws Exception {
        block(myToken, other.getId());
        mvc.perform(get("/api/products/{id}", otherProduct.getId()).header("Authorization", "Bearer " + myToken))
                .andExpect(status().isOk());
    }

    /** 한 방향이다. 내가 차단했다고 상대 화면에서 내 상품이 사라지지는 않는다(상대는 차단 사실을 모른다). */
    @Test
    void 차단당한_사람의_목록은_그대로다() throws Exception {
        block(myToken, other.getId());
        mvc.perform(get("/api/products").param("regionId", String.valueOf(region.getId()))
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    /** 비로그인에게는 차단이 없다. 목록 쿼리가 터지지 않는지도 함께 본다. */
    @Test
    void 비로그인_목록은_영향을_받지_않는다() throws Exception {
        block(myToken, other.getId());
        mvc.perform(get("/api/products").param("regionId", String.valueOf(region.getId())))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    // ---------- 효과 2: 채팅 ----------

    @Test
    void 차단하면_채팅방을_열_수_없다_양방향() throws Exception {
        block(myToken, other.getId());
        mvc.perform(post("/api/products/{id}/chat-rooms", otherProduct.getId()).header("Authorization", "Bearer " + myToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED_USER"));

        // 차단당한 쪽도 막힌다. 계속 말을 걸 수 있으면 차단이 아니다.
        Product myProduct = products.save(Product.builder().seller(me)
                .category(categories.findAll().getFirst()).region(region)
                .title("내 상품").description("차단당한 사람이 말을 걸 수 없어야 합니다.").price(5000).build());
        mvc.perform(post("/api/products/{id}/chat-rooms", myProduct.getId()).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED_USER"));
    }

    /**
     * 문구로 차단 사실이 새면 안 된다. 차단한 쪽에는 해제하면 된다고 알려 주고,
     * 차단당한 쪽에는 이유를 밝히지 않는다("차단" 이라는 말이 없어야 한다).
     */
    @Test
    void 차단당한_쪽의_문구에는_차단이라는_말이_없다() throws Exception {
        block(myToken, other.getId());
        Product myProduct = products.save(Product.builder().seller(me)
                .category(categories.findAll().getFirst()).region(region)
                .title("내 상품").description("차단당한 사람이 보는 문구를 확인합니다.").price(5000).build());

        mvc.perform(post("/api/products/{id}/chat-rooms", otherProduct.getId()).header("Authorization", "Bearer " + myToken))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("차단을 해제하면")));
        mvc.perform(post("/api/products/{id}/chat-rooms", myProduct.getId()).header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("차단"))));
    }

    @Test
    void 이미_있던_방에도_메시지를_보낼_수_없다() throws Exception {
        ChatRoom room = chatRooms.save(ChatRoom.open(otherProduct, me));
        block(myToken, other.getId());

        mvc.perform(post("/api/chat-rooms/{id}/messages", room.getId()).header("Authorization", "Bearer " + myToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"보내지면 안 됩니다\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED_USER"));
    }

    // ---------- 효과 3: 채팅 목록과 뱃지 ----------

    @Test
    void 차단하면_그_방이_목록과_뱃지에서_빠진다() throws Exception {
        ChatRoom room = chatRooms.save(ChatRoom.open(otherProduct, me));
        ChatMessage message = chatMessages.saveAndFlush(ChatMessage.text(room, other, "상대가 보낸 메시지"));
        room.recordMessage(message);   // 목록은 마지막 메시지가 있는 방만 보여 준다
        chatRooms.saveAndFlush(room);
        em.flush(); em.clear();

        mvc.perform(get("/api/chat-rooms").header("Authorization", "Bearer " + myToken))
                .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/chat-rooms/unread-count").header("Authorization", "Bearer " + myToken))
                .andExpect(jsonPath("$.count").value(1));

        block(myToken, other.getId());

        // 방과 대화는 지우지 않는다. 내 화면에서만 사라진다.
        mvc.perform(get("/api/chat-rooms").header("Authorization", "Bearer " + myToken))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/chat-rooms/unread-count").header("Authorization", "Bearer " + myToken))
                .andExpect(jsonPath("$.count").value(0));
        assertThat(chatRooms.findById(room.getId())).isPresent();
    }

    // 효과 4(알림)는 여기서 검증할 수 없다. 알림은 커밋 뒤에 만들어지는데 이 클래스는 롤백 트랜잭션이라
    // 필터를 꺼도 알림이 생기지 않아 테스트가 항상 통과한다(실제로 확인했다).
    // 커밋하는 전용 클래스에서 검증한다: NotificationFlowTest.차단한_사람이_일으킨_알림은_만들지_않는다
}
