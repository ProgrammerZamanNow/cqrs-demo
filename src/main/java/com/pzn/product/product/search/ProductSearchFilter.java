package com.pzn.product.product.search;

import com.pzn.product.product.Availability;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Kumpulan filter untuk Search Product (REQUIREMENT 6.3.2).
 */
public record ProductSearchFilter(
        String keyword,
        List<UUID> categoryIds,
        List<UUID> brandIds,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Availability availability
) {
}
