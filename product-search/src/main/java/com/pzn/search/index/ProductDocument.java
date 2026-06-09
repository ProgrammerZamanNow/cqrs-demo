package com.pzn.search.index;

/**
 * Dokumen produk di OpenSearch. Hanya menyimpan id kategori/brand; nama
 * di-resolve saat baca dari {@link RefCache} (read-time denormalization) sehingga
 * rename brand/kategori langsung tercermin tanpa reindex.
 */
public record ProductDocument(
        String id,
        String sku,
        String name,
        String description,
        double price,
        int stock,
        String imageUrl,
        String categoryId,
        String brandId,
        long createdAt,
        long updatedAt
) {
}
