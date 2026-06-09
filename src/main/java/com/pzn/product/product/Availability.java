package com.pzn.product.product;

/**
 * Dimensi ketersediaan stok untuk filter & facet search.
 * {@code IN_STOCK} = stock &gt; 0, {@code OUT_OF_STOCK} = stock = 0.
 */
public enum Availability {
    IN_STOCK,
    OUT_OF_STOCK
}
