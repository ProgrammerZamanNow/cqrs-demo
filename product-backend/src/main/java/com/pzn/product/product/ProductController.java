package com.pzn.product.product;

import com.pzn.product.product.dto.ProductRequest;
import com.pzn.product.product.dto.ProductResponse;
import com.pzn.product.product.dto.StockResponse;
import com.pzn.product.product.dto.StockUpdateRequest;
import com.pzn.product.product.search.ProductSearchFilter;
import com.pzn.product.product.search.ProductSearchService;
import com.pzn.product.product.search.ProductSearchService.ProductSearchResult;
import com.pzn.product.web.PageableSupport;
import com.pzn.product.web.PagingResponse;
import com.pzn.product.web.WebResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/products", produces = "application/json")
public class ProductController {

    private static final Set<String> SORTABLE =
            Set.of("name", "price", "stock", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    private final ProductService productService;
    private final ProductSearchService productSearchService;

    public ProductController(ProductService productService,
                            ProductSearchService productSearchService) {
        this.productService = productService;
        this.productSearchService = productSearchService;
    }

    @GetMapping
    public WebResponse<List<ProductResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<UUID> categoryId,
            @RequestParam(required = false) List<UUID> brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Availability availability,
            @RequestParam(required = false, defaultValue = "naive") String engine,
            @RequestParam(required = false, defaultValue = "true") boolean facet,
            @PageableDefault(size = 10) Pageable pageable) {
        ProductSearchFilter filter = new ProductSearchFilter(
                keyword, categoryId, brandId, minPrice, maxPrice, availability);
        Pageable sanitized = PageableSupport.sanitize(pageable, SORTABLE, DEFAULT_SORT);
        boolean trigram = "trigram".equalsIgnoreCase(engine);
        ProductSearchResult result = productSearchService.search(filter, sanitized, trigram, facet);
        return WebResponse.search(
                result.page().getContent(),
                PagingResponse.from(result.page()),
                result.facets());
    }

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public WebResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return WebResponse.ok(productService.create(request));
    }

    @GetMapping("/{id}")
    public WebResponse<ProductResponse> get(@PathVariable UUID id) {
        return WebResponse.ok(productService.get(id));
    }

    @PutMapping(path = "/{id}", consumes = "application/json")
    public WebResponse<ProductResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody ProductRequest request) {
        return WebResponse.ok(productService.update(id, request));
    }

    @PatchMapping(path = "/{id}/stock", consumes = "application/json")
    public WebResponse<StockResponse> updateStock(@PathVariable UUID id,
                                                  @Valid @RequestBody StockUpdateRequest request) {
        return WebResponse.ok(productService.updateStock(id, request));
    }

    @DeleteMapping("/{id}")
    public WebResponse<Object> delete(@PathVariable UUID id) {
        productService.delete(id);
        return WebResponse.deleted();
    }
}
