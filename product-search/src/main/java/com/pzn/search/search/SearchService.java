package com.pzn.search.search;

import com.pzn.search.index.RefCache;
import com.pzn.search.opensearch.IndexInitializer;
import com.pzn.search.opensearch.OpenSearchHttp;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Search produk ke OpenSearch. Facet memakai pola drill-down: tiap dimensi facet
 * dihitung dengan semua filter aktif KECUALI filter dimensinya sendiri
 * ({@code filter} aggregation), sementara {@code post_filter} menyaring hits.
 * Keyword pakai {@code match_phrase} di atas field n-gram (mirip LIKE substring).
 */
@Service
public class SearchService {

    private enum Dim { CATEGORY, BRAND, PRICE, AVAILABILITY }

    private record Bucket(Long min, Long max, String label) {
    }

    private static final List<Bucket> PRICE_BUCKETS = List.of(
            new Bucket(0L, 100_000L, "< 100.000"),
            new Bucket(100_000L, 500_000L, "100.000 - 500.000"),
            new Bucket(500_000L, null, "> 500.000")
    );

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "name.keyword",
            "price", "price",
            "stock", "stock",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt"
    );

    public record SearchParams(
            String keyword, List<String> categoryIds, List<String> brandIds,
            Double minPrice, Double maxPrice, String availability,
            int page, int size, String sort, boolean facet) {
    }

    public record SearchResult(List<ProductView> products, long total, Facets facets) {
    }

    private final OpenSearchHttp client;
    private final ObjectMapper mapper;
    private final RefCache refCache;

    public SearchService(OpenSearchHttp client, RefCache refCache) {
        this.client = client;
        this.mapper = client.mapper();
        this.refCache = refCache;
    }

    public SearchResult search(SearchParams p) {
        ObjectNode root = mapper.createObjectNode();
        root.put("size", p.size());
        root.put("from", p.page() * p.size());
        root.put("track_total_hits", true);
        root.set("query", keywordQuery(p.keyword()));

        ArrayNode allFilters = filterClauses(p, null);
        if (!allFilters.isEmpty()) root.set("post_filter", boolFilter(allFilters));

        root.set("sort", sortNode(p.sort()));

        // facet=false → lewati aggregations (isolasi biaya search murni dari facet).
        if (p.facet()) {
            ObjectNode aggs = mapper.createObjectNode();
            aggs.set("facet_categories", filterAgg(filterClauses(p, Dim.CATEGORY), termsAgg("categoryId")));
            aggs.set("facet_brands", filterAgg(filterClauses(p, Dim.BRAND), termsAgg("brandId")));
            aggs.set("facet_price", filterAgg(filterClauses(p, Dim.PRICE), priceAgg()));
            aggs.set("facet_availability", filterAgg(filterClauses(p, Dim.AVAILABILITY), availabilityAgg()));
            root.set("aggs", aggs);
        }

        JsonNode resp = client.search(IndexInitializer.INDEX, mapper.writeValueAsString(root));
        return parse(resp, p);
    }

    // ---------- query builders ----------

    private JsonNode keywordQuery(String keyword) {
        ObjectNode q = mapper.createObjectNode();
        if (keyword == null || keyword.isBlank()) {
            q.set("match_all", mapper.createObjectNode());
            return q;
        }
        ArrayNode should = mapper.createArrayNode();
        should.add(matchPhrase("name", keyword));
        should.add(matchPhrase("description", keyword));
        ObjectNode bool = mapper.createObjectNode();
        bool.set("should", should);
        bool.put("minimum_should_match", 1);
        q.set("bool", bool);
        return q;
    }

    private JsonNode matchPhrase(String field, String value) {
        ObjectNode inner = mapper.createObjectNode();
        inner.put(field, value);
        ObjectNode mp = mapper.createObjectNode();
        mp.set("match_phrase", inner);
        return mp;
    }

    private ArrayNode filterClauses(SearchParams p, Dim exclude) {
        ArrayNode arr = mapper.createArrayNode();
        if (exclude != Dim.CATEGORY && p.categoryIds() != null && !p.categoryIds().isEmpty()) {
            arr.add(terms("categoryId", p.categoryIds()));
        }
        if (exclude != Dim.BRAND && p.brandIds() != null && !p.brandIds().isEmpty()) {
            arr.add(terms("brandId", p.brandIds()));
        }
        if (exclude != Dim.PRICE && (p.minPrice() != null || p.maxPrice() != null)) {
            ObjectNode range = mapper.createObjectNode();
            if (p.minPrice() != null) range.put("gte", p.minPrice());
            if (p.maxPrice() != null) range.put("lte", p.maxPrice());
            ObjectNode priceField = mapper.createObjectNode();
            priceField.set("price", range);
            ObjectNode r = mapper.createObjectNode();
            r.set("range", priceField);
            arr.add(r);
        }
        if (exclude != Dim.AVAILABILITY && p.availability() != null) {
            arr.add("IN_STOCK".equals(p.availability())
                    ? rangeClause("stock", "gt", 0)
                    : termClause("stock", 0));
        }
        return arr;
    }

    private JsonNode terms(String field, List<String> values) {
        ArrayNode vals = mapper.createArrayNode();
        values.forEach(vals::add);
        ObjectNode inner = mapper.createObjectNode();
        inner.set(field, vals);
        ObjectNode t = mapper.createObjectNode();
        t.set("terms", inner);
        return t;
    }

    private JsonNode rangeClause(String field, String op, int value) {
        ObjectNode cmp = mapper.createObjectNode();
        cmp.put(op, value);
        ObjectNode f = mapper.createObjectNode();
        f.set(field, cmp);
        ObjectNode r = mapper.createObjectNode();
        r.set("range", f);
        return r;
    }

    private JsonNode termClause(String field, int value) {
        ObjectNode inner = mapper.createObjectNode();
        inner.put(field, value);
        ObjectNode t = mapper.createObjectNode();
        t.set("term", inner);
        return t;
    }

    private JsonNode boolFilter(ArrayNode filters) {
        ObjectNode bool = mapper.createObjectNode();
        bool.set("filter", filters);
        ObjectNode q = mapper.createObjectNode();
        q.set("bool", bool);
        return q;
    }

    private ObjectNode filterAgg(ArrayNode filters, ObjectNode subAggs) {
        ObjectNode agg = mapper.createObjectNode();
        agg.set("filter", filters.isEmpty() ? matchAll() : boolFilter(filters));
        agg.set("aggs", subAggs);
        return agg;
    }

    private JsonNode matchAll() {
        ObjectNode q = mapper.createObjectNode();
        q.set("match_all", mapper.createObjectNode());
        return q;
    }

    private ObjectNode termsAgg(String field) {
        ObjectNode terms = mapper.createObjectNode();
        terms.put("field", field);
        terms.put("size", 1000);
        ObjectNode ids = mapper.createObjectNode();
        ids.set("terms", terms);
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.set("ids", ids);
        return wrapper;
    }

    private ObjectNode priceAgg() {
        ArrayNode ranges = mapper.createArrayNode();
        for (Bucket b : PRICE_BUCKETS) {
            ObjectNode r = mapper.createObjectNode();
            if (b.min() != null && b.min() > 0) r.put("from", b.min());
            if (b.max() != null) r.put("to", b.max());
            ranges.add(r);
        }
        ObjectNode range = mapper.createObjectNode();
        range.put("field", "price");
        range.set("ranges", ranges);
        ObjectNode rangesAgg = mapper.createObjectNode();
        rangesAgg.set("range", range);
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.set("ranges", rangesAgg);
        return wrapper;
    }

    private ObjectNode availabilityAgg() {
        ObjectNode wrapper = mapper.createObjectNode();
        ObjectNode inStock = mapper.createObjectNode();
        inStock.set("filter", rangeClause("stock", "gt", 0));
        ObjectNode outStock = mapper.createObjectNode();
        outStock.set("filter", termClause("stock", 0));
        wrapper.set("in_stock", inStock);
        wrapper.set("out_of_stock", outStock);
        return wrapper;
    }

    private JsonNode sortNode(String sort) {
        String field = "name";
        String dir = "asc";
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            if (SORT_FIELDS.containsKey(parts[0])) field = parts[0];
            if (parts.length > 1 && ("asc".equals(parts[1]) || "desc".equals(parts[1]))) dir = parts[1];
        }
        ObjectNode order = mapper.createObjectNode();
        order.put("order", dir);
        ObjectNode entry = mapper.createObjectNode();
        entry.set(SORT_FIELDS.get(field), order);
        ArrayNode sortArr = mapper.createArrayNode();
        sortArr.add(entry);
        return sortArr;
    }

    // ---------- response parsing ----------

    private SearchResult parse(JsonNode resp, SearchParams p) {
        long total = resp.path("hits").path("total").path("value").asLong();

        List<ProductView> products = new ArrayList<>();
        for (JsonNode hit : resp.path("hits").path("hits")) {
            JsonNode s = hit.path("_source");
            String categoryId = text(s, "categoryId");
            String brandId = text(s, "brandId");
            products.add(new ProductView(
                    text(s, "id"),
                    text(s, "sku"),
                    text(s, "name"),
                    text(s, "description"),
                    s.path("price").asDouble(),
                    s.path("stock").asInt(),
                    text(s, "imageUrl"),
                    new ProductView.Ref(categoryId, refCache.categoryName(categoryId)),
                    new ProductView.Ref(brandId, refCache.brandName(brandId)),
                    s.path("createdAt").asLong(),
                    s.path("updatedAt").asLong()
            ));
        }

        Facets facets = null;
        if (p.facet()) {
            facets = total == 0 ? Facets.empty() : parseFacets(resp, p);
        }
        return new SearchResult(products, total, facets);
    }

    private Facets parseFacets(JsonNode resp, SearchParams p) {
        JsonNode aggs = resp.path("aggregations");
        Set<String> selCat = Set.copyOf(p.categoryIds() == null ? List.of() : p.categoryIds());
        Set<String> selBrand = Set.copyOf(p.brandIds() == null ? List.of() : p.brandIds());

        List<Facets.FacetItem> categories = new ArrayList<>();
        for (JsonNode b : aggs.path("facet_categories").path("ids").path("buckets")) {
            String id = text(b, "key");
            categories.add(new Facets.FacetItem(id, refCache.categoryName(id),
                    b.path("doc_count").asLong(), selCat.contains(id)));
        }
        List<Facets.FacetItem> brands = new ArrayList<>();
        for (JsonNode b : aggs.path("facet_brands").path("ids").path("buckets")) {
            String id = text(b, "key");
            brands.add(new Facets.FacetItem(id, refCache.brandName(id),
                    b.path("doc_count").asLong(), selBrand.contains(id)));
        }

        List<Facets.PriceRangeFacet> priceRanges = new ArrayList<>();
        JsonNode priceBuckets = aggs.path("facet_price").path("ranges").path("buckets");
        for (int i = 0; i < PRICE_BUCKETS.size(); i++) {
            Bucket def = PRICE_BUCKETS.get(i);
            long count = priceBuckets.path(i).path("doc_count").asLong();
            priceRanges.add(new Facets.PriceRangeFacet(def.min(), def.max(), def.label(), count, false));
        }

        long inStock = aggs.path("facet_availability").path("in_stock").path("doc_count").asLong();
        long outStock = aggs.path("facet_availability").path("out_of_stock").path("doc_count").asLong();
        List<Facets.AvailabilityFacet> availability = List.of(
                new Facets.AvailabilityFacet("IN_STOCK", inStock, "IN_STOCK".equals(p.availability())),
                new Facets.AvailabilityFacet("OUT_OF_STOCK", outStock, "OUT_OF_STOCK".equals(p.availability()))
        );

        return new Facets(categories, brands, priceRanges, availability);
    }

    private String text(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isNull() || n.isMissingNode() ? null : n.asString();
    }
}
