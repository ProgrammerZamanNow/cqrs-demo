package com.pzn.product.category;

import com.pzn.product.category.dto.CategoryRequest;
import com.pzn.product.category.dto.CategoryResponse;
import com.pzn.product.exception.ConflictException;
import com.pzn.product.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;

    public CategoryService(CategoryRepository categoryRepository, EntityManager entityManager) {
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new ConflictException("Category with name '" + request.name() + "' already exists");
        }
        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(Pageable pageable) {
        return categoryRepository.findAll(pageable).map(CategoryResponse::from);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return CategoryResponse.from(findOrThrow(id));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        Category category = findOrThrow(id);
        if (categoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("Category with name '" + request.name() + "' already exists");
        }
        category.setName(request.name());
        category.setDescription(request.description());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        Category category = findOrThrow(id);
        long productCount = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM products WHERE category_id = :id")
                .setParameter("id", id)
                .getSingleResult()).longValue();
        if (productCount > 0) {
            throw new ConflictException("Category still referenced by " + productCount + " product(s)");
        }
        categoryRepository.delete(category);
    }

    private Category findOrThrow(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category with id " + id + " not found"));
    }
}
