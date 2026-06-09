package com.pzn.product.category;

import com.pzn.product.AbstractIntegrationTest;
import com.pzn.product.brand.Brand;
import com.pzn.product.category.dto.CategoryRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryApiTest extends AbstractIntegrationTest {

    private static final String MISSING_ID = "00000000-0000-0000-0000-000000000000";

    @Test
    void create_returns201_withEnvelopeMetadataAndHeader() throws Exception {
        mockMvc.perform(post("/api/categories").contentType("application/json")
                        .content(json(new CategoryRequest("Electronics", "Electronic devices"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name", is("Electronics")))
                .andExpect(jsonPath("$.data.description", is("Electronic devices")))
                .andExpect(jsonPath("$.data.createdAt").isNumber())
                .andExpect(jsonPath("$.data.updatedAt").isNumber())
                .andExpect(jsonPath("$.metadata.processTimeMs", greaterThanOrEqualTo(0)))
                .andExpect(header().exists("X-Process-Time-Ms"));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/categories").contentType("application/json")
                        .content(json(new CategoryRequest("  ", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
    }

    @Test
    void create_nameTooLong_returns400() throws Exception {
        mockMvc.perform(post("/api/categories").contentType("application/json")
                        .content(json(new CategoryRequest("x".repeat(101), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void create_duplicateName_returns409() throws Exception {
        CategoryRequest request = new CategoryRequest("Books", null);
        mockMvc.perform(post("/api/categories").contentType("application/json").content(json(request)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/categories").contentType("application/json").content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void list_returnsPaging() throws Exception {
        newCategory("Garden");
        newCategory("Toys");
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.paging.totalElement", is(2)))
                .andExpect(jsonPath("$.paging.page", is(0)))
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
    }

    @Test
    void getDetail_returns200() throws Exception {
        Category saved = newCategory("Music");
        mockMvc.perform(get("/api/categories/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(saved.getId().toString())))
                .andExpect(jsonPath("$.data.name", is("Music")));
    }

    @Test
    void getDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/categories/{id}", MISSING_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void update_returns200_withNewData() throws Exception {
        Category saved = newCategory("Old");
        mockMvc.perform(put("/api/categories/{id}", saved.getId()).contentType("application/json")
                        .content(json(new CategoryRequest("New", "updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("New")))
                .andExpect(jsonPath("$.data.description", is("updated")));
    }

    @Test
    void update_duplicateName_returns409() throws Exception {
        newCategory("Alpha");
        Category beta = newCategory("Beta");
        mockMvc.perform(put("/api/categories/{id}", beta.getId()).contentType("application/json")
                        .content(json(new CategoryRequest("Alpha", null))))
                .andExpect(status().isConflict());
    }

    @Test
    void update_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/categories/{id}", MISSING_ID).contentType("application/json")
                        .content(json(new CategoryRequest("Whatever", null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns200_withNullDataAndMetadata() throws Exception {
        Category saved = newCategory("Removable");
        mockMvc.perform(delete("/api/categories/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
        mockMvc.perform(get("/api/categories/{id}", saved.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", MISSING_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_referencedByProduct_returns409() throws Exception {
        Category category = newCategory("Used");
        Brand brand = newBrand("BrandX");
        newProduct("SKU-C", "Item", new BigDecimal("1000"), 1, category, brand);

        mockMvc.perform(delete("/api/categories/{id}", category.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }
}
