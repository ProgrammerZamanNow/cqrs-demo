package com.pzn.product.product.dto;

import com.pzn.product.product.StockChangeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body untuk PATCH /api/products/{id}/stock (REQUIREMENT 6.3.5).
 */
public record StockUpdateRequest(

        @NotNull(message = "must not be null")
        @Positive(message = "must be greater than 0")
        Integer quantity,

        @NotNull(message = "must not be null")
        StockChangeType type
) {
}
