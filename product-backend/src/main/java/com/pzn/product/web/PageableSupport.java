package com.pzn.product.web;

import com.pzn.product.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/**
 * Aturan pagination & sorting standar (REQUIREMENT 6.1):
 * <ul>
 *   <li>{@code size > 100} → ditolak (400).</li>
 *   <li>{@code sort} ke field yang tidak diizinkan → diabaikan, pakai default.</li>
 *   <li>{@code page} di luar range → bukan error (ditangani layer query).</li>
 * </ul>
 */
public final class PageableSupport {

    public static final int MAX_SIZE = 100;

    private PageableSupport() {
    }

    public static Pageable sanitize(Pageable pageable, Set<String> allowedSortFields, Sort defaultSort) {
        if (pageable.getPageSize() > MAX_SIZE) {
            throw new BadRequestException("size must be less than or equal to " + MAX_SIZE);
        }

        List<Sort.Order> validOrders = pageable.getSort().stream()
                .filter(order -> allowedSortFields.contains(order.getProperty()))
                .toList();

        Sort sort = validOrders.isEmpty() ? defaultSort : Sort.by(validOrders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
