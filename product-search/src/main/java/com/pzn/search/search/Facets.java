package com.pzn.search.search;

import java.util.List;

/** Facet — kontrak identik dengan product-backend. */
public record Facets(
        List<FacetItem> categories,
        List<FacetItem> brands,
        List<PriceRangeFacet> priceRanges,
        List<AvailabilityFacet> availability
) {

    public static Facets empty() {
        return new Facets(List.of(), List.of(), List.of(), List.of());
    }

    public record FacetItem(String id, String name, long count, boolean selected) {
    }

    public record PriceRangeFacet(Long min, Long max, String label, long count, boolean selected) {
    }

    public record AvailabilityFacet(String value, long count, boolean selected) {
    }
}
