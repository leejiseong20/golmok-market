package com.golmok.market.domain.category.dto;

import com.golmok.market.domain.category.Category;

public record CategoryResponse(Long id, String name, String iconUrl, int sortOrder) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getIconUrl(), category.getSortOrder());
    }
}
