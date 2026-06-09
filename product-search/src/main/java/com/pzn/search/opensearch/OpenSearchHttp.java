package com.pzn.search.opensearch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Klien HTTP tipis ke OpenSearch (java.net.http) — cukup untuk create index,
 * bulk index, dan search dengan body JSON mentah.
 */
@Component
public class OpenSearchHttp {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String base;
    private final ObjectMapper mapper;

    public OpenSearchHttp(@Value("${opensearch.uri}") String base, ObjectMapper mapper) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.mapper = mapper;
    }

    public boolean indexExists(String index) {
        try {
            HttpResponse<Void> res = http.send(
                    HttpRequest.newBuilder(URI.create(base + "/" + index))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding());
            return res.statusCode() == 200;
        } catch (Exception e) {
            throw new OpenSearchException("HEAD " + index + " failed", e);
        }
    }

    public void put(String path, String jsonBody) {
        send("PUT", path, jsonBody, "application/json");
    }

    /** Bulk pakai content-type ndjson. Body harus diakhiri newline. */
    public JsonNode bulk(String ndjson) {
        return send("POST", "/_bulk", ndjson, "application/x-ndjson");
    }

    public JsonNode search(String index, String queryJson) {
        return send("POST", "/" + index + "/_search", queryJson, "application/json");
    }

    public JsonNode getJson(String path) {
        return send("GET", path, null, "application/json");
    }

    private JsonNode send(String method, String path, String body, String contentType) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                    .header("Content-Type", contentType)
                    .timeout(Duration.ofSeconds(60));
            b.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                throw new OpenSearchException(method + " " + path + " -> " + res.statusCode() + ": " + res.body());
            }
            return res.body() == null || res.body().isBlank() ? null : mapper.readTree(res.body());
        } catch (OpenSearchException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenSearchException(method + " " + path + " failed", e);
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
