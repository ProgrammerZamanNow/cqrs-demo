package com.pzn.product.web;

/**
 * Menyimpan waktu mulai pemrosesan request per-thread, diisi oleh
 * {@link ProcessTimeFilter} dan dibaca oleh {@link ProcessTimeResponseAdvice}.
 */
public final class ProcessTimeContext {

    private static final ThreadLocal<Long> START_NANOS = new ThreadLocal<>();

    private ProcessTimeContext() {
    }

    public static void start(long nanos) {
        START_NANOS.set(nanos);
    }

    public static long elapsedMillis(long nowNanos) {
        Long start = START_NANOS.get();
        if (start == null) {
            return 0L;
        }
        return (nowNanos - start) / 1_000_000L;
    }

    public static void clear() {
        START_NANOS.remove();
    }
}
