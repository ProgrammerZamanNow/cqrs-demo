package com.pzn.product.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body untuk Create & Update Category. Hanya {@code name} yang wajib (maks 100).
 */
public record CategoryRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "size must be less than or equal to 100")
        String name,

        String description
) {
}
