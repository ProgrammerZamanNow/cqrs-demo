package com.pzn.product.product;

import com.pzn.product.product.dto.ProductRequest;
import com.pzn.product.product.dto.ProductResponse;
import com.pzn.product.product.dto.StockResponse;
import com.pzn.product.product.dto.StockUpdateRequest;
import com.pzn.product.web.WebResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(path = "/api/products", produces = "application/json")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
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
