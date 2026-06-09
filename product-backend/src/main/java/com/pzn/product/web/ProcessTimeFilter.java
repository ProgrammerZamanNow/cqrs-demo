package com.pzn.product.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Mencatat waktu mulai request sehingga {@link ProcessTimeResponseAdvice} bisa
 * menghitung {@code processTimeMs} secara terpusat (NFR-08).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProcessTimeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ProcessTimeContext.start(System.nanoTime());
        try {
            filterChain.doFilter(request, response);
        } finally {
            ProcessTimeContext.clear();
        }
    }
}
