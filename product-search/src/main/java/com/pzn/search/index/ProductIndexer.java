package com.pzn.search.index;

import com.pzn.search.opensearch.IndexInitializer;
import com.pzn.search.opensearch.OpenSearchHttp;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Menulis dokumen produk ke OpenSearch secara bulk (index / delete).
 */
@Component
public class ProductIndexer {

    private final OpenSearchHttp client;
    private final ObjectMapper mapper;

    public ProductIndexer(OpenSearchHttp client) {
        this.client = client;
        this.mapper = client.mapper();
    }

    public void bulk(List<ProductDocument> upserts, List<String> deletes) {
        if (upserts.isEmpty() && deletes.isEmpty()) return;

        StringBuilder nd = new StringBuilder();
        for (ProductDocument doc : upserts) {
            nd.append("{\"index\":{\"_index\":\"").append(IndexInitializer.INDEX)
                    .append("\",\"_id\":\"").append(doc.id()).append("\"}}\n");
            nd.append(mapper.writeValueAsString(doc)).append('\n');
        }
        for (String id : deletes) {
            nd.append("{\"delete\":{\"_index\":\"").append(IndexInitializer.INDEX)
                    .append("\",\"_id\":\"").append(id).append("\"}}\n");
        }
        client.bulk(nd.toString());
    }
}
