package com.pzn.search.opensearch;

public class OpenSearchException extends RuntimeException {

    public OpenSearchException(String message) {
        super(message);
    }

    public OpenSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
