package com.pzn.product.brand;

import com.pzn.product.brand.dto.BrandRequest;
import com.pzn.product.brand.dto.BrandResponse;
import com.pzn.product.web.PageableSupport;
import com.pzn.product.web.PagingResponse;
import com.pzn.product.web.WebResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/brands", produces = "application/json")
public class BrandController {

    private static final Set<String> SORTABLE = Set.of("name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public WebResponse<BrandResponse> create(@Valid @RequestBody BrandRequest request) {
        return WebResponse.ok(brandService.create(request));
    }

    @GetMapping
    public WebResponse<List<BrandResponse>> list(@PageableDefault(size = 10) Pageable pageable) {
        Page<BrandResponse> page = brandService.list(
                PageableSupport.sanitize(pageable, SORTABLE, DEFAULT_SORT));
        return WebResponse.list(page.getContent(), PagingResponse.from(page));
    }

    @GetMapping("/{id}")
    public WebResponse<BrandResponse> get(@PathVariable UUID id) {
        return WebResponse.ok(brandService.get(id));
    }

    @PutMapping(path = "/{id}", consumes = "application/json")
    public WebResponse<BrandResponse> update(@PathVariable UUID id,
                                             @Valid @RequestBody BrandRequest request) {
        return WebResponse.ok(brandService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public WebResponse<Object> delete(@PathVariable UUID id) {
        brandService.delete(id);
        return WebResponse.deleted();
    }
}
