package com.pzn.search.search;

/** Bentuk produk pada hasil search — identik dengan ProductResponse di product-backend. */
public record ProductView(
        String id,
        String sku,
        String name,
        String description,
        double price,
        int stock,
        String imageUrl,
        Ref category,
        Ref brand,
        Long createdAt,
        Long updatedAt
) {
    public record Ref(String id, String name) {
    }
}
