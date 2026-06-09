package com.pzn.product.web;

/**
 * Metadata yang menyertai setiap respons API. {@code processTimeMs} adalah lama
 * proses request di backend dalam milidetik.
 */
public record Metadata(long processTimeMs) {
}
