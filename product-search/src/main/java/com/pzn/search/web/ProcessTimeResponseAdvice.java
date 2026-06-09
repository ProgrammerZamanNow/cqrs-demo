package com.pzn.search.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ProcessTimeResponseAdvice implements ResponseBodyAdvice<Object> {

    public static final String HEADER = "X-Process-Time-Ms";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof WebResponse<?> webResponse && webResponse.metadata() == null) {
            long ms = ProcessTimeContext.elapsedMillis(System.nanoTime());
            response.getHeaders().add(HEADER, Long.toString(ms));
            return webResponse.withMetadata(new Metadata(ms));
        }
        return body;
    }
}
