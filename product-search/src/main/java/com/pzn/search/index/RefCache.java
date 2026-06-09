package com.pzn.search.index;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache nama kategori & brand (id → nama), diisi dari topik CDC. Dipakai untuk
 * resolve nama saat membaca hasil search & facet. Kecil (≤150 entri), selalu
 * lengkap karena topik dikonsumsi dari earliest.
 */
@Component
public class RefCache {

    private final Map<String, String> categories = new ConcurrentHashMap<>();
    private final Map<String, String> brands = new ConcurrentHashMap<>();

    public void putCategory(String id, String name) {
        if (id != null && name != null) categories.put(id, name);
    }

    public void removeCategory(String id) {
        if (id != null) categories.remove(id);
    }

    public String categoryName(String id) {
        return id == null ? null : categories.get(id);
    }

    public void putBrand(String id, String name) {
        if (id != null && name != null) brands.put(id, name);
    }

    public void removeBrand(String id) {
        if (id != null) brands.remove(id);
    }

    public String brandName(String id) {
        return id == null ? null : brands.get(id);
    }

    public int categoryCount() {
        return categories.size();
    }

    public int brandCount() {
        return brands.size();
    }
}
