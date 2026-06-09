package com.pzn.search.web;

public final class ProcessTimeContext {

    private static final ThreadLocal<Long> START_NANOS = new ThreadLocal<>();

    private ProcessTimeContext() {
    }

    public static void start(long nanos) {
        START_NANOS.set(nanos);
    }

    public static long elapsedMillis(long nowNanos) {
        Long start = START_NANOS.get();
        return start == null ? 0L : (nowNanos - start) / 1_000_000L;
    }

    public static void clear() {
        START_NANOS.remove();
    }
}
