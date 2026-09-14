package com.golmok.market.domain.region;

import com.golmok.market.domain.region.dto.RegionResponse;
import com.golmok.market.global.error.BusinessException;
import com.golmok.market.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class RegionServiceTest {

    @Autowired RegionService regionService;
    @Autowired RegionRepository regionRepository;
    @Autowired EntityManager em;

    private Region save(String dong, double lat, double lng) {
        return regionRepository.save(Region.create("서울특별시", "강남구", dong, lat, lng));
    }

    @Test
    void 생성_시각은_JPA가_채우고_재조회해도_유지된다() {
        Region region = save("역삼동", 37.5006, 127.0366);
        LocalDateTime createdAt = region.getCreatedAt();
        assertThat(createdAt).isNotNull();
        em.flush();
        em.clear();

        assertThat(regionRepository.findById(region.getId()).orElseThrow().getCreatedAt())
                .isEqualToIgnoringNanos(createdAt);
    }

    @Test
    void 시도_시군구_동과_전체_주소를_부분_검색하고_앞뒤_공백은_제거한다() {
        Region region = save("역삼동", 37.5006, 127.0366);
        for (String keyword : List.of("서울", "강남", "역삼", "강남구 역삼", " 서울특별시 강남구 역삼동 ")) {
            assertThat(regionService.search(keyword)).extracting(RegionResponse::id).containsExactly(region.getId());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "!%_"})
    void 검색어의_와일드카드와_이스케이프_문자는_문자_그대로_찾는다(String literal) {
        Region matching = save("특수" + literal + "동", 0, 0);
        save("일반동", 0, 0);

        assertThat(regionService.search(literal)).extracting(RegionResponse::id).containsExactly(matching.getId());
    }

    @Test
    void 검색_결과는_id순이고_일치하는_동네가_없으면_빈_목록이다() {
        Region first = save("역삼동", 0, 0);
        Region second = save("가나다동", 0, 0);

        assertThat(regionService.search("동")).extracting(RegionResponse::id).containsExactly(first.getId(), second.getId());
        assertThat(regionService.search("없는동네")).isEmpty();
    }

    @Test
    void 동네가_없으면_근처_조회도_빈_목록이다() {
        assertThat(regionService.findNearby(37.5, 127)).isEmpty();
    }

    @Test
    void 초기_동네_3개는_반경을_확대해서_거리순으로_모두_반환한다() {
        Region seogyo = regionRepository.save(Region.create("서울특별시", "마포구", "서교동", 37.5520, 126.9180));
        Region seongsu = regionRepository.save(Region.create("서울특별시", "성동구", "성수동", 37.5446, 127.0559));
        Region yeoksam = save("역삼동", 37.5006, 127.0366);

        assertThat(regionService.findNearby(37.5006, 127.0366)).extracting(RegionResponse::id)
                .containsExactly(yeoksam.getId(), seongsu.getId(), seogyo.getId());
    }

    @Test
    void 동거리는_id순으로_최대_10개만_반환한다() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(save("동" + i, 37.5, 127).getId());
        }

        assertThat(regionService.findNearby(37.5, 127)).extracting(RegionResponse::id)
                .containsExactlyElementsOf(ids.subList(0, 10));
    }

    @Test
    void 사각형_모서리에_10개가_있어도_더_가까운_사각형_밖_동네를_놓치지_않는다() {
        // 5km 사각형 안의 모서리는 약 6.3km, 사각형 바로 밖의 북쪽 동네는 약 5.6km다.
        for (int i = 0; i < 10; i++) {
            save("모서리" + i, 0.04, 0.04);
        }
        Region closer = save("북쪽", 0.05, 0);

        List<RegionResponse> result = regionService.findNearby(0, 0);
        assertThat(result).hasSize(10);
        assertThat(result.getFirst().id()).isEqualTo(closer.getId());
    }

    @Test
    void 초기_반경에_후보가_없어도_먼_동네를_찾는다() {
        List<Long> expected = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            expected.add(save("먼동네" + i, 1 + i * 0.01, 0).getId());
        }

        assertThat(regionService.findNearby(0, 0)).extracting(RegionResponse::id)
                .containsExactlyElementsOf(expected.subList(0, 10));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void 날짜변경선_양쪽의_동네를_누락없이_조회한다(int sign) {
        List<Long> closer = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            save("같은쪽" + i, 0, sign * 179.96);
            closer.add(save("건너편" + i, 0, -sign * 179.999).getId());
        }

        assertThat(regionService.findNearby(0, sign * 179.999)).extracting(RegionResponse::id)
                .containsExactlyElementsOf(closer);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void 극점을_포함하는_범위는_반대_경도의_가까운_동네도_찾는다(int sign) {
        List<Long> closer = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            save("같은경도" + i, sign * 89.96, 0);
            closer.add(save("반대경도" + i, sign * 89.999, 180).getId());
        }

        assertThat(regionService.findNearby(sign * 89.999, 0)).extracting(RegionResponse::id)
                .containsExactlyElementsOf(closer);
    }

    @Test
    void 서비스_직접_호출도_잘못된_입력을_BusinessException으로_거부한다() {
        assertThatThrownBy(() -> regionService.search(" "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> regionService.findNearby(Double.NaN, 127))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
