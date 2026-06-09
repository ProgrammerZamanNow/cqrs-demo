package com.pzn.product.web;

import org.springframework.data.domain.Page;

/**
 * Metadata pagination standar (lihat REQUIREMENT 6.1).
 */
public record PagingResponse(
        int page,
        int size,
        long totalElement,
        int totalPage
) {

    public static PagingResponse from(Page<?> page) {
        return new PagingResponse(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
