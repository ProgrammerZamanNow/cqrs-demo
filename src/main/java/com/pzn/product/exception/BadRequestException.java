package com.pzn.product.exception;

/**
 * Dilempar untuk error validasi/permintaan tidak valid yang ditangani manual
 * (mis. stok jadi negatif, size &gt; 100) → HTTP 400.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
