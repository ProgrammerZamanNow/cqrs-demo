package com.pzn.product.product;

import com.pzn.product.brand.Brand;
import com.pzn.product.brand.BrandRepository;
import com.pzn.product.category.Category;
import com.pzn.product.category.CategoryRepository;
import com.pzn.product.exception.BadRequestException;
import com.pzn.product.exception.ConflictException;
import com.pzn.product.exception.NotFoundException;
import com.pzn.product.product.dto.ProductRequest;
import com.pzn.product.product.dto.ProductResponse;
import com.pzn.product.product.dto.StockResponse;
import com.pzn.product.product.dto.StockUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService {

    static final String DEFAULT_IMAGE_URL =
            "https://dummyimage.com/600x400/cccccc/000000&text=No+Image";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("SKU '" + request.sku() + "' already exists");
        }
        Product product = new Product();
        applyRequest(product, request);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductResponse.from(findOrThrow(id));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = findOrThrow(id);
        if (productRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new ConflictException("SKU '" + request.sku() + "' already exists");
        }
        applyRequest(product, request);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void delete(UUID id) {
        Product product = findOrThrow(id);
        productRepository.delete(product);
    }

    @Transactional
    public StockResponse updateStock(UUID id, StockUpdateRequest request) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Product with id " + id + " not found"));

        int newStock = request.type() == StockChangeType.INCREASE
                ? product.getStock() + request.quantity()
                : product.getStock() - request.quantity();

        if (newStock < 0) {
            throw new BadRequestException("stock cannot be negative");
        }
        product.setStock(newStock);
        return StockResponse.from(productRepository.save(product));
    }

    private void applyRequest(Product product, ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException(
                        "Category with id " + request.categoryId() + " not found"));
        Brand brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new NotFoundException(
                        "Brand with id " + request.brandId() + " not found"));

        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock() == null ? 0 : request.stock());
        product.setImageUrl(resolveImageUrl(request.imageUrl()));
        product.setCategory(category);
        product.setBrand(brand);
    }

    private String resolveImageUrl(String imageUrl) {
        return (imageUrl == null || imageUrl.isBlank()) ? DEFAULT_IMAGE_URL : imageUrl;
    }

    private Product findOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product with id " + id + " not found"));
    }
}
