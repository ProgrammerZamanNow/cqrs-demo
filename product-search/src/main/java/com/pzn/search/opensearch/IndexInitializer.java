package com.pzn.search.opensearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Membuat index `products` saat startup bila belum ada.
 *
 * <p>Field {@code name} & {@code description} memakai analyzer <b>n-gram</b> agar
 * pencarian substring (mirip {@code LIKE '%kata%'} di PostgreSQL) bisa dilayani
 * dari index. Query memakai {@code match_phrase} sehingga n-gram harus berurutan
 * → cocok substring panjang berapa pun.
 */
@Component
public class IndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);
    public static final String INDEX = "products";

    private static final String MAPPING = """
            {
              "settings": {
                "index": {
                  "number_of_shards": 1,
                  "number_of_replicas": 0,
                  "max_ngram_diff": 2
                },
                "analysis": {
                  "tokenizer": {
                    "substring_ngram": { "type": "ngram", "min_gram": 2, "max_gram": 3 }
                  },
                  "analyzer": {
                    "substring": {
                      "type": "custom",
                      "tokenizer": "substring_ngram",
                      "filter": ["lowercase"]
                    }
                  }
                }
              },
              "mappings": {
                "properties": {
                  "id":          { "type": "keyword" },
                  "sku":         { "type": "keyword" },
                  "name":        { "type": "text", "analyzer": "substring", "search_analyzer": "substring",
                                   "fields": { "keyword": { "type": "keyword" } } },
                  "description": { "type": "text", "analyzer": "substring", "search_analyzer": "substring" },
                  "price":       { "type": "double" },
                  "stock":       { "type": "integer" },
                  "imageUrl":    { "type": "keyword", "index": false },
                  "categoryId":  { "type": "keyword" },
                  "brandId":     { "type": "keyword" },
                  "createdAt":   { "type": "long" },
                  "updatedAt":   { "type": "long" }
                }
              }
            }
            """;

    private final OpenSearchHttp client;

    public IndexInitializer(OpenSearchHttp client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (client.indexExists(INDEX)) {
            log.info("OpenSearch index '{}' sudah ada", INDEX);
            return;
        }
        client.put("/" + INDEX, MAPPING);
        log.info("OpenSearch index '{}' dibuat (ngram analyzer)", INDEX);
    }
}
