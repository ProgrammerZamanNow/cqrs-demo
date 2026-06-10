package com.pzn.product.product.search;

import com.pzn.product.brand.Brand;
import com.pzn.product.category.Category;
import com.pzn.product.product.Product;
import com.pzn.product.product.ProductRepository;
import com.pzn.product.product.dto.ProductResponse;
import com.pzn.product.product.search.ProductFacets.AvailabilityFacet;
import com.pzn.product.product.search.ProductFacets.FacetItem;
import com.pzn.product.product.search.ProductFacets.PriceRangeFacet;
import com.pzn.product.product.search.ProductPredicates.Dimension;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pencarian produk + perhitungan facet dengan drill-down (REQUIREMENT 6.3.2).
 */
@Service
public class ProductSearchService {

    private record Bucket(BigDecimal min, BigDecimal max, String label) {
    }

    private static final List<Bucket> PRICE_BUCKETS = List.of(
            new Bucket(BigDecimal.ZERO, new BigDecimal("100000"), "< 100.000"),
            new Bucket(new BigDecimal("100000"), new BigDecimal("500000"), "100.000 - 500.000"),
            new Bucket(new BigDecimal("500000"), null, "> 500.000")
    );

    public record ProductSearchResult(Page<ProductResponse> page, ProductFacets facets) {
    }

    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    public ProductSearchService(ProductRepository productRepository, EntityManager entityManager) {
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    /**
     * @param trigram bila {@code true}, biarkan planner memakai GIN trigram index
     *                (pencarian cepat ala n-gram). Bila {@code false} (naif/default),
     *                bitmap scan dimatikan untuk transaksi ini sehingga keyword
     *                {@code LIKE '%..%'} kembali sequential scan — memperagakan
     *                kondisi "tanpa trigram".
     * @param facet   bila {@code false}, lewati perhitungan facet (mengisolasi biaya
     *                search murni dari biaya agregasi facet). Respons tanpa {@code facets}.
     */
    @Transactional(readOnly = true)
    public ProductSearchResult search(ProductSearchFilter filter, Pageable pageable,
                                      boolean trigram, boolean facet) {
        if (!trigram) {
            // GIN trigram hanya bisa dipakai lewat bitmap scan → mematikannya
            // memaksa keyword search kembali ke sequential scan (perilaku naif).
            entityManager.unwrap(Session.class).doWork(connection -> {
                try (Statement st = connection.createStatement()) {
                    st.execute("SET LOCAL enable_bitmapscan = off");
                }
            });
        }

        Specification<Product> spec = ProductPredicates.specification(filter);
        Page<Product> page = productRepository.findAll(spec, pageable);

        ProductFacets facets = null;
        if (facet) {
            facets = page.getTotalElements() == 0
                    ? ProductFacets.empty()
                    : new ProductFacets(
                            categoryFacet(filter),
                            brandFacet(filter),
                            priceRangeFacet(filter),
                            availabilityFacet(filter));
        }

        return new ProductSearchResult(page.map(ProductResponse::from), facets);
    }

    private List<FacetItem> categoryFacet(ProductSearchFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> root = cq.from(Product.class);
        Join<Product, Category> category = root.join("category");

        List<Predicate> predicates = ProductPredicates.build(root, cb, filter, EnumSet.of(Dimension.CATEGORY));
        cq.multiselect(category.get("id"), category.get("name"), cb.count(root));
        if (!predicates.isEmpty()) {
            cq.where(predicates.toArray(Predicate[]::new));
        }
        cq.groupBy(category.get("id"), category.get("name"));
        cq.orderBy(cb.desc(cb.count(root)));

        Set<UUID> selected = selectedIds(filter.categoryIds());
        return entityManager.createQuery(cq).getResultList().stream()
                .map(t -> new FacetItem(
                        t.get(0, UUID.class),
                        t.get(1, String.class),
                        t.get(2, Long.class),
                        selected.contains(t.get(0, UUID.class))))
                .toList();
    }

    private List<FacetItem> brandFacet(ProductSearchFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Product> root = cq.from(Product.class);
        Join<Product, Brand> brand = root.join("brand");

        List<Predicate> predicates = ProductPredicates.build(root, cb, filter, EnumSet.of(Dimension.BRAND));
        cq.multiselect(brand.get("id"), brand.get("name"), cb.count(root));
        if (!predicates.isEmpty()) {
            cq.where(predicates.toArray(Predicate[]::new));
        }
        cq.groupBy(brand.get("id"), brand.get("name"));
        cq.orderBy(cb.desc(cb.count(root)));

        Set<UUID> selected = selectedIds(filter.brandIds());
        return entityManager.createQuery(cq).getResultList().stream()
                .map(t -> new FacetItem(
                        t.get(0, UUID.class),
                        t.get(1, String.class),
                        t.get(2, Long.class),
                        selected.contains(t.get(0, UUID.class))))
                .toList();
    }

    private List<PriceRangeFacet> priceRangeFacet(ProductSearchFilter filter) {
        List<PriceRangeFacet> result = new ArrayList<>();
        for (Bucket bucket : PRICE_BUCKETS) {
            long count = countInPriceBucket(filter, bucket);
            result.add(new PriceRangeFacet(bucket.min(), bucket.max(), bucket.label(), count, false));
        }
        return result;
    }

    private long countInPriceBucket(ProductSearchFilter filter, Bucket bucket) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Product> root = cq.from(Product.class);

        List<Predicate> predicates = new ArrayList<>(
                ProductPredicates.build(root, cb, filter, EnumSet.of(Dimension.PRICE)));
        predicates.add(cb.greaterThanOrEqualTo(root.get("price"), bucket.min()));
        if (bucket.max() != null) {
            predicates.add(cb.lessThan(root.get("price"), bucket.max()));
        }
        cq.select(cb.count(root)).where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(cq).getSingleResult();
    }

    private List<AvailabilityFacet> availabilityFacet(ProductSearchFilter filter) {
        long inStock = countAvailability(filter, true);
        long outOfStock = countAvailability(filter, false);
        boolean inSelected = filter.availability() == com.pzn.product.product.Availability.IN_STOCK;
        boolean outSelected = filter.availability() == com.pzn.product.product.Availability.OUT_OF_STOCK;
        return List.of(
                new AvailabilityFacet("IN_STOCK", inStock, inSelected),
                new AvailabilityFacet("OUT_OF_STOCK", outOfStock, outSelected)
        );
    }

    private long countAvailability(ProductSearchFilter filter, boolean inStock) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Product> root = cq.from(Product.class);

        List<Predicate> predicates = new ArrayList<>(
                ProductPredicates.build(root, cb, filter, EnumSet.of(Dimension.AVAILABILITY)));
        predicates.add(inStock
                ? cb.greaterThan(root.get("stock"), 0)
                : cb.equal(root.get("stock"), 0));
        cq.select(cb.count(root)).where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(cq).getSingleResult();
    }

    private Set<UUID> selectedIds(List<UUID> ids) {
        return ids == null ? Set.of() : ids.stream().collect(Collectors.toUnmodifiableSet());
    }
}
