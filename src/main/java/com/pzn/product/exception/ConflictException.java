package com.pzn.product.exception;

/**
 * Dilempar saat terjadi konflik (mis. nama/SKU duplikat, atau menghapus
 * resource yang masih direferensikan) → HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
