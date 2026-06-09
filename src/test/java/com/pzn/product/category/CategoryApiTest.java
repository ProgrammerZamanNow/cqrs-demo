package com.pzn.product.category;

import com.pzn.product.AbstractIntegrationTest;
import com.pzn.product.category.dto.CategoryRequest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryApiTest extends AbstractIntegrationTest {

    @Test
    void createCategory_returns201_withEnvelopeAndMetadata() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CategoryRequest("Electronics", "Electronic devices"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name", is("Electronics")))
                .andExpect(jsonPath("$.data.createdAt").isNumber())
                .andExpect(jsonPath("$.metadata.processTimeMs", greaterThanOrEqualTo(0)));
    }

    @Test
    void createCategory_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CategoryRequest("  ", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
    }

    @Test
    void createCategory_duplicateName_returns409() throws Exception {
        CategoryRequest request = new CategoryRequest("Books", null);
        mockMvc.perform(post("/api/categories")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categories")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getCategory_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/categories/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void deleteCategory_returns200_withNullDataAndMetadata() throws Exception {
        Category saved = newCategory("Toys");

        mockMvc.perform(delete("/api/categories/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
    }

    @Test
    void listCategory_returnsPaging() throws Exception {
        newCategory("Garden");

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.paging.totalElement", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return categoryRepository.save(category);
    }
}
