package com.golmok.market.domain.category;

import com.golmok.market.domain.category.dto.CategoryResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CategoryServiceTest {

    @Autowired CategoryService categoryService;
    @Autowired CategoryRepository categoryRepository;
    @Autowired EntityManager em;

    @Test
    void 표시_순서가_같으면_id_순으로_정렬하고_자식도_포함한다() {
        Category root = categoryRepository.save(Category.create(null, "가구", null, 2));
        Category child = categoryRepository.save(Category.create(root, "의자", "https://example.com/chair.png", 1));
        Category digital = categoryRepository.save(Category.create(null, "디지털", null, 1));
        em.flush();
        em.clear();

        assertThat(categoryService.findAll()).extracting(CategoryResponse::id)
                .containsExactly(child.getId(), digital.getId(), root.getId());
        assertThat(categoryService.findAll().getFirst().iconUrl()).isEqualTo("https://example.com/chair.png");
    }

    @Test
    void 카테고리가_없으면_빈_목록이다() {
        assertThat(categoryService.findAll()).isEmpty();
    }
}
