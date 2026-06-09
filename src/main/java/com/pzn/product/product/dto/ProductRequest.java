package com.pzn.product.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body untuk Create & Update Product (REQUIREMENT 6.3.1 / 6.3.4).
 */
public record ProductRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "size must be less than or equal to 50")
        String sku,

        @NotBlank(message = "must not be blank")
        @Size(max = 150, message = "size must be less than or equal to 150")
        String name,

        String description,

        @NotNull(message = "must not be null")
        @DecimalMin(value = "0.0", message = "must be greater than or equal to 0")
        BigDecimal price,

        @Min(value = 0, message = "must be greater than or equal to 0")
        Integer stock,

        @Size(max = 500, message = "size must be less than or equal to 500")
        String imageUrl,

        @NotNull(message = "must not be null")
        UUID categoryId,

        @NotNull(message = "must not be null")
        UUID brandId
) {
}
