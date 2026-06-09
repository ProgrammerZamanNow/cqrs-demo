package com.pzn.product.product;

import com.pzn.product.AbstractIntegrationTest;
import com.pzn.product.brand.Brand;
import com.pzn.product.category.Category;
import com.pzn.product.product.dto.ProductRequest;
import com.pzn.product.product.dto.StockUpdateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductApiTest extends AbstractIntegrationTest {

    private static final String MISSING_ID = "00000000-0000-0000-0000-000000000000";

    private ProductRequest sampleRequest(String sku, UUID categoryId, UUID brandId, String imageUrl) {
        return new ProductRequest(sku, "Wireless Mouse", "2.4GHz", new BigDecimal("150000.00"),
                50, imageUrl, categoryId, brandId);
    }

    // ---- create ----

    @Test
    void create_returns201_withNestedRefsAndProvidedImage() throws Exception {
        Category category = newCategory("Electronics");
        Brand brand = newBrand("Logitech");

        mockMvc.perform(post("/api/products").contentType("application/json")
                        .content(json(sampleRequest("SKU-1", category.getId(), brand.getId(),
                                "https://cdn/x.png"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.sku", is("SKU-1")))
                .andExpect(jsonPath("$.data.price", is(150000.00)))
                .andExpect(jsonPath("$.data.stock", is(50)))
                .andExpect(jsonPath("$.data.imageUrl", is("https://cdn/x.png")))
                .andExpect(jsonPath("$.data.category.id", is(category.getId().toString())))
                .andExpect(jsonPath("$.data.category.name", is("Electronics")))
                .andExpect(jsonPath("$.data.brand.name", is("Logitech")))
                .andExpect(jsonPath("$.data.createdAt").isNumber())
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
    }

    @Test
    void create_blankImageUrl_usesDefaultImage() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        mockMvc.perform(post("/api/products").contentType("application/json")
                        .content(json(sampleRequest("SKU-2", category.getId(), brand.getId(), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.imageUrl", containsString("dummyimage.com")));
    }

    @Test
    void create_duplicateSku_returns409() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        ProductRequest request = sampleRequest("SKU-DUP", category.getId(), brand.getId(), null);
        mockMvc.perform(post("/api/products").contentType("application/json").content(json(request)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/products").contentType("application/json").content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("SKU")));
    }

    @Test
    void create_categoryNotFound_returns404() throws Exception {
        Brand brand = newBrand("Nike");
        mockMvc.perform(post("/api/products").contentType("application/json")
                        .content(json(sampleRequest("SKU-3", UUID.fromString(MISSING_ID), brand.getId(), null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("Category")));
    }

    @Test
    void create_brandNotFound_returns404() throws Exception {
        Category category = newCategory("Shoes");
        mockMvc.perform(post("/api/products").contentType("application/json")
                        .content(json(sampleRequest("SKU-4", category.getId(), UUID.fromString(MISSING_ID), null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("Brand")));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        ProductRequest request = new ProductRequest("SKU-5", "  ", null, new BigDecimal("1000"),
                1, null, category.getId(), brand.getId());
        mockMvc.perform(post("/api/products").contentType("application/json").content(json(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_negativePrice_returns400() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        ProductRequest request = new ProductRequest("SKU-6", "Item", null, new BigDecimal("-1"),
                1, null, category.getId(), brand.getId());
        mockMvc.perform(post("/api/products").contentType("application/json").content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("price")));
    }

    @Test
    void create_missingPrice_returns400() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        ProductRequest request = new ProductRequest("SKU-7", "Item", null, null,
                1, null, category.getId(), brand.getId());
        mockMvc.perform(post("/api/products").contentType("application/json").content(json(request)))
                .andExpect(status().isBadRequest());
    }

    // ---- read ----

    @Test
    void getDetail_returns200() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        var product = newProduct("SKU-G", "Sepatu", new BigDecimal("100000"), 5, category, brand);
        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(product.getId().toString())))
                .andExpect(jsonPath("$.data.sku", is("SKU-G")));
    }

    @Test
    void getDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/products/{id}", MISSING_ID))
                .andExpect(status().isNotFound());
    }

    // ---- update ----

    @Test
    void update_returns200_withNewData() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        var product = newProduct("SKU-U", "Old", new BigDecimal("100000"), 5, category, brand);

        ProductRequest request = new ProductRequest("SKU-U", "New Name", "desc",
                new BigDecimal("200000.00"), 40, "https://cdn/y.png", category.getId(), brand.getId());
        mockMvc.perform(put("/api/products/{id}", product.getId()).contentType("application/json")
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("New Name")))
                .andExpect(jsonPath("$.data.price", is(200000.00)))
                .andExpect(jsonPath("$.data.stock", is(40)));
    }

    @Test
    void update_duplicateSku_returns409() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        newProduct("SKU-A", "A", new BigDecimal("1000"), 1, category, brand);
        var productB = newProduct("SKU-B", "B", new BigDecimal("1000"), 1, category, brand);

        ProductRequest request = sampleRequest("SKU-A", category.getId(), brand.getId(), null);
        mockMvc.perform(put("/api/products/{id}", productB.getId()).contentType("application/json")
                        .content(json(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void update_notFound_returns404() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        mockMvc.perform(put("/api/products/{id}", MISSING_ID).contentType("application/json")
                        .content(json(sampleRequest("SKU-X", category.getId(), brand.getId(), null))))
                .andExpect(status().isNotFound());
    }

    // ---- delete (hard) ----

    @Test
    void delete_returns200_thenGetReturns404() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        var product = newProduct("SKU-D", "Item", new BigDecimal("1000"), 1, category, brand);

        mockMvc.perform(delete("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", MISSING_ID))
                .andExpect(status().isNotFound());
    }

    // ---- stock ----

    @Test
    void stock_increase_returns200() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        var product = newProduct("SKU-S1", "Item", new BigDecimal("1000"), 10, category, brand);

        mockMvc.perform(patch("/api/products/{id}/stock", product.getId()).contentType("application/json")
                        .content(json(new StockUpdateRequest(5, StockChangeType.INCREASE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock", is(15)));
    }

    @Test
    void stock_decrease_returns200() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        var product = newProduct("SKU-S2", "Item", new BigDecimal("1000"), 10, category, brand);

        mockMvc.perform(patch("/api/products/{id}/stock", product.getId()).contentType("application/json")
                        .content(json(new StockUpdateRequest(4, StockChangeType.DECREASE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock", is(6)));
    }

    @Test
    void stock_decreaseBelowZero_returns400() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        var product = newProduct("SKU-S3", "Item", new BigDecimal("1000"), 3, category, brand);

        mockMvc.perform(patch("/api/products/{id}/stock", product.getId()).contentType("application/json")
                        .content(json(new StockUpdateRequest(5, StockChangeType.DECREASE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("negative")));
    }

    @Test
    void stock_invalidQuantity_returns400() throws Exception {
        Category category = newCategory("Shoes");
        Brand brand = newBrand("Nike");
        var product = newProduct("SKU-S4", "Item", new BigDecimal("1000"), 3, category, brand);

        mockMvc.perform(patch("/api/products/{id}/stock", product.getId()).contentType("application/json")
                        .content(json(new StockUpdateRequest(0, StockChangeType.INCREASE))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stock_notFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/products/{id}/stock", MISSING_ID).contentType("application/json")
                        .content(json(new StockUpdateRequest(1, StockChangeType.INCREASE))))
                .andExpect(status().isNotFound());
    }
}
