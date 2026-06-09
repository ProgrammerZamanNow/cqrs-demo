package com.pzn.product.brand;

import com.pzn.product.brand.dto.BrandRequest;
import com.pzn.product.brand.dto.BrandResponse;
import com.pzn.product.exception.ConflictException;
import com.pzn.product.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BrandService {

    private final BrandRepository brandRepository;
    private final EntityManager entityManager;

    public BrandService(BrandRepository brandRepository, EntityManager entityManager) {
        this.brandRepository = brandRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        if (brandRepository.existsByName(request.name())) {
            throw new ConflictException("Brand with name '" + request.name() + "' already exists");
        }
        Brand brand = new Brand();
        brand.setName(request.name());
        brand.setDescription(request.description());
        return BrandResponse.from(brandRepository.save(brand));
    }

    @Transactional(readOnly = true)
    public Page<BrandResponse> list(Pageable pageable) {
        return brandRepository.findAll(pageable).map(BrandResponse::from);
    }

    @Transactional(readOnly = true)
    public BrandResponse get(UUID id) {
        return BrandResponse.from(findOrThrow(id));
    }

    @Transactional
    public BrandResponse update(UUID id, BrandRequest request) {
        Brand brand = findOrThrow(id);
        if (brandRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ConflictException("Brand with name '" + request.name() + "' already exists");
        }
        brand.setName(request.name());
        brand.setDescription(request.description());
        return BrandResponse.from(brandRepository.save(brand));
    }

    @Transactional
    public void delete(UUID id) {
        Brand brand = findOrThrow(id);
        long productCount = ((Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM products WHERE brand_id = :id")
                .setParameter("id", id)
                .getSingleResult()).longValue();
        if (productCount > 0) {
            throw new ConflictException("Brand still referenced by " + productCount + " product(s)");
        }
        brandRepository.delete(brand);
    }

    private Brand findOrThrow(UUID id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Brand with id " + id + " not found"));
    }
}
