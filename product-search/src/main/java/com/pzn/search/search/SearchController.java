package com.pzn.search.search;

import com.pzn.search.search.SearchService.SearchParams;
import com.pzn.search.search.SearchService.SearchResult;
import com.pzn.search.web.PagingResponse;
import com.pzn.search.web.WebResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Read API CQRS — kontrak query & envelope identik dengan
 * {@code GET /api/products} di product-backend, tapi dilayani dari OpenSearch.
 */
@RestController
@RequestMapping(path = "/api/products", produces = "application/json")
public class SearchController {

    private static final Set<String> AVAILABILITY = Set.of("IN_STOCK", "OUT_OF_STOCK");

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/_search")
    public WebResponse<List<ProductView>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<String> categoryId,
            @RequestParam(required = false) List<String> brandId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String availability,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name,asc") String sort,
            @RequestParam(defaultValue = "true") boolean facet) {

        if (size > 100) {
            throw new IllegalArgumentException("size must be less than or equal to 100");
        }
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 10 : size;
        String avail = availability != null && AVAILABILITY.contains(availability) ? availability : null;

        SearchParams params = new SearchParams(
                keyword, categoryId, brandId, minPrice, maxPrice, avail, safePage, safeSize, sort, facet);
        SearchResult result = searchService.search(params);

        return WebResponse.search(
                result.products(),
                PagingResponse.of(safePage, safeSize, result.total()),
                result.facets());
    }
}
