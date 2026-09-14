package com.golmok.market.domain.category;

import com.golmok.market.domain.category.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> findAll() {
        // 부모 연관관계에 접근하지 않고 전체를 평면 배열로 반환한다.
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
