package com.pzn.product.product.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Facet hasil pencarian beserta jumlah produk per item (REQUIREMENT 6.3.2).
 */
public record ProductFacets(
        List<FacetItem> categories,
        List<FacetItem> brands,
        List<PriceRangeFacet> priceRanges,
        List<AvailabilityFacet> availability
) {

    public static ProductFacets empty() {
        return new ProductFacets(List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Item facet untuk dimensi berbasis entitas (category/brand).
     */
    public record FacetItem(UUID id, String name, long count, boolean selected) {
    }

    /**
     * Item facet rentang harga (bucket statis). {@code max} null = tak terbatas.
     */
    public record PriceRangeFacet(BigDecimal min, BigDecimal max, String label, long count, boolean selected) {
    }

    /**
     * Item facet ketersediaan stok.
     */
    public record AvailabilityFacet(String value, long count, boolean selected) {
    }
}
