package com.pzn.product.product.dto;

import com.pzn.product.product.Product;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Representasi produk pada respons. {@code category} & {@code brand} berupa
 * referensi ringkas {id, name}. Timestamp berupa epoch millis (UTC).
 */
public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int stock,
        String imageUrl,
        Ref category,
        Ref brand,
        Long createdAt,
        Long updatedAt
) {

    public record Ref(UUID id, String name) {
    }

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl(),
                new Ref(product.getCategory().getId(), product.getCategory().getName()),
                new Ref(product.getBrand().getId(), product.getBrand().getName()),
                product.getCreatedAt().toEpochMilli(),
                product.getUpdatedAt().toEpochMilli()
        );
    }
}
