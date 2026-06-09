package com.pzn.search.web;

/** Metadata pagination, kontrak identik dengan product-backend. */
public record PagingResponse(int page, int size, long totalElement, int totalPage) {

    public static PagingResponse of(int page, int size, long totalElement) {
        int totalPage = size <= 0 ? 0 : (int) Math.ceil((double) totalElement / size);
        return new PagingResponse(page, size, totalElement, totalPage);
    }
}
