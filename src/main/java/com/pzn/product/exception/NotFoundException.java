package com.pzn.product.exception;

/**
 * Dilempar saat resource tidak ditemukan → HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
