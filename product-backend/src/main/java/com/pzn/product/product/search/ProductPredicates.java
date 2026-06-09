package com.pzn.product.product.search;

import com.pzn.product.product.Availability;
import com.pzn.product.product.Product;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Membangun predicate filter produk. Mendukung pengecualian satu/lebih dimensi —
 * dipakai untuk drill-down facet, di mana count sebuah facet dihitung dengan
 * semua filter aktif KECUALI filter dari dimensi facet itu sendiri (REQUIREMENT 6.3.2).
 */
public final class ProductPredicates {

    public enum Dimension {
        KEYWORD, CATEGORY, BRAND, PRICE, AVAILABILITY
    }

    private ProductPredicates() {
    }

    public static List<Predicate> build(Root<Product> root,
                                        CriteriaBuilder cb,
                                        ProductSearchFilter filter,
                                        Set<Dimension> exclude) {
        List<Predicate> predicates = new ArrayList<>();

        if (!exclude.contains(Dimension.KEYWORD) && hasText(filter.keyword())) {
            String like = "%" + filter.keyword().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("description")), like)
            ));
        }

        if (!exclude.contains(Dimension.CATEGORY) && notEmpty(filter.categoryIds())) {
            predicates.add(root.get("category").get("id").in(filter.categoryIds()));
        }

        if (!exclude.contains(Dimension.BRAND) && notEmpty(filter.brandIds())) {
            predicates.add(root.get("brand").get("id").in(filter.brandIds()));
        }

        if (!exclude.contains(Dimension.PRICE)) {
            if (filter.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.minPrice()));
            }
            if (filter.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.maxPrice()));
            }
        }

        if (!exclude.contains(Dimension.AVAILABILITY) && filter.availability() != null) {
            if (filter.availability() == Availability.IN_STOCK) {
                predicates.add(cb.greaterThan(root.get("stock"), 0));
            } else {
                predicates.add(cb.equal(root.get("stock"), 0));
            }
        }

        return predicates;
    }

    /**
     * Specification untuk query produk utama (semua filter diterapkan).
     */
    public static Specification<Product> specification(ProductSearchFilter filter) {
        return (root, query, cb) -> cb.and(
                build(root, cb, filter, Set.of()).toArray(Predicate[]::new));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
