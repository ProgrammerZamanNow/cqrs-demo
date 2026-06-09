package com.pzn.product.brand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body untuk Create & Update Brand. Hanya {@code name} yang wajib (maks 100).
 */
public record BrandRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "size must be less than or equal to 100")
        String name,

        String description
) {
}
