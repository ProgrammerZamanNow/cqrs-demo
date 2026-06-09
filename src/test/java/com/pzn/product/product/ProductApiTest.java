package com.pzn.product.product;

import com.pzn.product.AbstractIntegrationTest;
import com.pzn.product.brand.Brand;
import com.pzn.product.category.Category;
import com.pzn.product.product.dto.ProductRequest;
import com.pzn.product.product.dto.StockUpdateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductApiTest extends AbstractIntegrationTest {

    @Test
    void createProduct_blankImageUrl_usesDefaultImage() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");

        ProductRequest request = new ProductRequest(
                "SKU-1", "Sepatu Lari", "ringan", new BigDecimal("250000.00"),
                10, null, category.getId(), brand.getId());

        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sku", is("SKU-1")))
                .andExpect(jsonPath("$.data.imageUrl", containsString("dummyimage.com")))
                .andExpect(jsonPath("$.data.category.name", is("Shoes")))
                .andExpect(jsonPath("$.data.brand.name", is("Nike")))
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
    }

    @Test
    void updateStock_decreaseBelowZero_returns400() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        Product product = newProduct("SKU-2", "Sepatu", new BigDecimal("100000"), 3, category, brand);

        mockMvc.perform(patch("/api/products/{id}/stock", product.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new StockUpdateRequest(5, StockChangeType.DECREASE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("negative")));
    }

    @Test
    void search_drillDown_keepsOtherBrandsInFacet() throws Exception {
        Category shoes = newCategory("Shoes");
        Brand nike = newBrand("Nike");
        Brand adidas = newBrand("Adidas");

        newProduct("NK-1", "sepatu lari nike", new BigDecimal("300000"), 5, shoes, nike);
        newProduct("NK-2", "sepatu basket nike", new BigDecimal("450000"), 5, shoes, nike);
        newProduct("AD-1", "sepatu bola adidas", new BigDecimal("250000"), 5, shoes, adidas);
        newProduct("AD-2", "sepatu lari adidas", new BigDecimal("350000"), 5, shoes, adidas);
        newProduct("AD-3", "sepatu futsal adidas", new BigDecimal("200000"), 5, shoes, adidas);

        // Filter brand=Nike: data hanya Nike (2), tetapi facet brand tetap memuat Nike & Adidas.
        mockMvc.perform(get("/api/products")
                        .param("keyword", "sepatu")
                        .param("brandId", nike.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.facets.brands", hasSize(2)))
                // drill-down: Adidas tetap muncul dengan count penuh (3), tidak hilang
                .andExpect(jsonPath("$.facets.brands[?(@.name=='Adidas')].count", contains(3)))
                .andExpect(jsonPath("$.facets.brands[?(@.name=='Nike')].count", contains(2)))
                .andExpect(jsonPath("$.facets.brands[?(@.name=='Nike')].selected", contains(true)))
                .andExpect(jsonPath("$.paging.totalElement", is(2)))
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
    }

    @Test
    void search_availabilityFacet_countsInAndOutOfStock() throws Exception {
        Category shoes = newCategory("Shoes");
        Brand nike = newBrand("Nike");
        newProduct("S-1", "sepatu a", new BigDecimal("100000"), 5, shoes, nike);
        newProduct("S-2", "sepatu b", new BigDecimal("100000"), 0, shoes, nike);

        mockMvc.perform(get("/api/products").param("keyword", "sepatu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facets.availability[?(@.value=='IN_STOCK')].count", contains(1)))
                .andExpect(jsonPath("$.facets.availability[?(@.value=='OUT_OF_STOCK')].count", contains(1)));
    }

    @Test
    void search_sizeOver100_returns400() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("100")));
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return categoryRepository.save(category);
    }

    private Brand newBrand(String name) {
        Brand brand = new Brand();
        brand.setName(name);
        return brandRepository.save(brand);
    }

    private Product newProduct(String sku, String name, BigDecimal price, int stock,
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
}
