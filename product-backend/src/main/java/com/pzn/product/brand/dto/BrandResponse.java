package com.pzn.product.brand.dto;

import com.pzn.product.brand.Brand;

import java.util.UUID;

/**
 * Representasi brand pada respons. Timestamp berupa epoch millis (UTC).
 */
public record BrandResponse(
        UUID id,
        String name,
        String description,
        Long createdAt,
        Long updatedAt
) {

    public static BrandResponse from(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getDescription(),
                brand.getCreatedAt().toEpochMilli(),
                brand.getUpdatedAt().toEpochMilli()
        );
    }
}
