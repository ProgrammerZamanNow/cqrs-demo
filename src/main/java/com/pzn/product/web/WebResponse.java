package com.pzn.product.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope respons standar (lihat REQUIREMENT 6.1).
 *
 * <ul>
 *   <li>{@code data}     — payload sukses (object atau array); {@code null} pada DELETE.</li>
 *   <li>{@code paging}   — hanya pada endpoint list yang mendukung pagination.</li>
 *   <li>{@code facets}   — hanya pada Search Product.</li>
 *   <li>{@code error}    — hanya pada respons gagal (string pesan).</li>
 *   <li>{@code metadata} — selalu ada; diisi terpusat oleh {@link ProcessTimeResponseAdvice}.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebResponse<T>(
        T data,
        PagingResponse paging,
        Object facets,
        String error,
        Metadata metadata
) {

    public static <T> WebResponse<T> ok(T data) {
        return new WebResponse<>(data, null, null, null, null);
    }

    public static <T> WebResponse<T> list(T data, PagingResponse paging) {
        return new WebResponse<>(data, paging, null, null, null);
    }

    public static <T> WebResponse<T> search(T data, PagingResponse paging, Object facets) {
        return new WebResponse<>(data, paging, facets, null, null);
    }

    public static WebResponse<Object> deleted() {
        return new WebResponse<>(null, null, null, null, null);
    }

    public static WebResponse<Object> error(String message) {
        return new WebResponse<>(null, null, null, message, null);
    }

    public WebResponse<T> withMetadata(Metadata metadata) {
        return new WebResponse<>(data, paging, facets, error, metadata);
    }
}
