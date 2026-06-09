package com.pzn.product.category.dto;

import com.pzn.product.category.Category;

import java.util.UUID;

/**
 * Representasi category pada respons. Timestamp berupa epoch millis (UTC).
 */
public record CategoryResponse(
        UUID id,
        String name,
        String description,
        Long createdAt,
        Long updatedAt
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getCreatedAt().toEpochMilli(),
                category.getUpdatedAt().toEpochMilli()
        );
    }
}
