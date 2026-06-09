package com.pzn.product.product.dto;

import com.pzn.product.product.Product;

import java.util.UUID;

/**
 * Respons ringkas setelah update stok (REQUIREMENT 6.3.5).
 */
public record StockResponse(
        UUID id,
        String sku,
        int stock,
        Long updatedAt
) {

    public static StockResponse from(Product product) {
        return new StockResponse(
                product.getId(),
                product.getSku(),
                product.getStock(),
                product.getUpdatedAt().toEpochMilli()
        );
    }
}
