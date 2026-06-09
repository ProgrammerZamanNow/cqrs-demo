package com.pzn.product.category;

import com.pzn.product.category.dto.CategoryRequest;
import com.pzn.product.category.dto.CategoryResponse;
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
@RequestMapping(path = "/api/categories", produces = "application/json")
public class CategoryController {

    private static final Set<String> SORTABLE = Set.of("name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public WebResponse<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return WebResponse.ok(categoryService.create(request));
    }

    @GetMapping
    public WebResponse<List<CategoryResponse>> list(@PageableDefault(size = 10) Pageable pageable) {
        Page<CategoryResponse> page = categoryService.list(
                PageableSupport.sanitize(pageable, SORTABLE, DEFAULT_SORT));
        return WebResponse.list(page.getContent(), PagingResponse.from(page));
    }

    @GetMapping("/{id}")
    public WebResponse<CategoryResponse> get(@PathVariable UUID id) {
        return WebResponse.ok(categoryService.get(id));
    }

    @PutMapping(path = "/{id}", consumes = "application/json")
    public WebResponse<CategoryResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody CategoryRequest request) {
        return WebResponse.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public WebResponse<Object> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return WebResponse.deleted();
    }
}
