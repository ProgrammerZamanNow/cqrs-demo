package com.pzn.search.cdc;

import com.pzn.search.index.ProductDocument;
import com.pzn.search.index.ProductIndexer;
import com.pzn.search.index.RefCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mengonsumsi event CDC Debezium (sudah di-unwrap) dari Kafka dan memproyeksikan
 * ke OpenSearch. Kategori & brand masuk {@link RefCache}; produk di-index bulk.
 */
@Component
public class CdcConsumer {

    private static final Logger log = LoggerFactory.getLogger(CdcConsumer.class);

    private final ProductIndexer indexer;
    private final RefCache refCache;
    private final ObjectMapper mapper;

    public CdcConsumer(ProductIndexer indexer, RefCache refCache, ObjectMapper mapper) {
        this.indexer = indexer;
        this.refCache = refCache;
        this.mapper = mapper;
    }

    // group unik per boot: reference data (kecil) selalu dibaca penuh dari earliest
    // agar cache nama lengkap meski instance/offset berganti.
    @KafkaListener(topics = "${cdc.topic.categories}", groupId = "product-search-categories-${random.uuid}")
    public void onCategories(List<String> messages) {
        for (String msg : messages) {
            Map<String, Object> row = parse(msg);
            if (row == null) continue;
            String id = str(row, "id");
            if (isDeleted(row)) refCache.removeCategory(id);
            else refCache.putCategory(id, str(row, "name"));
        }
    }

    @KafkaListener(topics = "${cdc.topic.brands}", groupId = "product-search-brands-${random.uuid}")
    public void onBrands(List<String> messages) {
        for (String msg : messages) {
            Map<String, Object> row = parse(msg);
            if (row == null) continue;
            String id = str(row, "id");
            if (isDeleted(row)) refCache.removeBrand(id);
            else refCache.putBrand(id, str(row, "name"));
        }
    }

    @KafkaListener(topics = "${cdc.topic.products}", groupId = "product-search-products")
    public void onProducts(List<String> messages) {
        List<ProductDocument> upserts = new ArrayList<>();
        List<String> deletes = new ArrayList<>();
        for (String msg : messages) {
            Map<String, Object> row = parse(msg);
            if (row == null) continue;
            String id = str(row, "id");
            if (isDeleted(row)) {
                deletes.add(id);
            } else {
                upserts.add(toDocument(row));
            }
        }
        indexer.bulk(upserts, deletes);
        log.debug("indexed {} upserts, {} deletes", upserts.size(), deletes.size());
    }

    private ProductDocument toDocument(Map<String, Object> row) {
        return new ProductDocument(
                str(row, "id"),
                str(row, "sku"),
                str(row, "name"),
                str(row, "description"),
                dbl(row, "price"),
                (int) lng(row, "stock"),
                str(row, "image_url"),
                str(row, "category_id"),
                str(row, "brand_id"),
                lng(row, "created_at"),
                lng(row, "updated_at")
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String msg) {
        if (msg == null || msg.isBlank()) return null;
        try {
            return mapper.readValue(msg, Map.class);
        } catch (Exception e) {
            log.warn("gagal parse CDC message: {}", e.getMessage());
            return null;
        }
    }

    private boolean isDeleted(Map<String, Object> row) {
        return "true".equals(String.valueOf(row.get("__deleted"))) || "d".equals(row.get("__op"));
    }

    private String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }

    private double dbl(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v instanceof Number n ? n.doubleValue() : 0d;
    }

    private long lng(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
