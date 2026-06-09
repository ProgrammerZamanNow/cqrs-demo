package com.pzn.search.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Envelope identik dengan product-backend (data/paging/facets/error/metadata). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebResponse<T>(T data, PagingResponse paging, Object facets, String error, Metadata metadata) {

    public static <T> WebResponse<T> search(T data, PagingResponse paging, Object facets) {
        return new WebResponse<>(data, paging, facets, null, null);
    }

    public static WebResponse<Object> error(String message) {
        return new WebResponse<>(null, null, null, message, null);
    }

    public WebResponse<T> withMetadata(Metadata metadata) {
        return new WebResponse<>(data, paging, facets, error, metadata);
    }
}
