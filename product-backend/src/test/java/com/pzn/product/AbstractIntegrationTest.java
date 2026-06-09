package com.pzn.product;

import com.pzn.product.brand.Brand;
import com.pzn.product.brand.BrandRepository;
import com.pzn.product.category.Category;
import com.pzn.product.category.CategoryRepository;
import com.pzn.product.product.Product;
import com.pzn.product.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

/**
 * Basis integration test: full Spring context + MockMvc + PostgreSQL (Testcontainers).
 * Database dibersihkan sebelum tiap test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected BrandRepository brandRepository;

    @BeforeEach
    void cleanDatabase() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();
    }

    protected Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return categoryRepository.save(category);
    }

    protected Brand newBrand(String name) {
        Brand brand = new Brand();
        brand.setName(name);
        return brandRepository.save(brand);
    }

    protected Product newProduct(String sku, String name, BigDecimal price, int stock,
                                 Category category, Brand brand) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setPrice(price);
        product.setStock(stock);
        product.setImageUrl("https://example.com/img.png");
        product.setCategory(category);
        product.setBrand(brand);
        return productRepository.save(product);
    }

    protected String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
