package com.pzn.product.brand;

import com.pzn.product.AbstractIntegrationTest;
import com.pzn.product.brand.dto.BrandRequest;
import com.pzn.product.category.Category;
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

class BrandApiTest extends AbstractIntegrationTest {

    private static final String MISSING_ID = "00000000-0000-0000-0000-000000000000";

    @Test
    void create_returns201_withEnvelopeMetadataAndHeader() throws Exception {
        mockMvc.perform(post("/api/brands").contentType("application/json")
                        .content(json(new BrandRequest("Logitech", "Peripherals"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name", is("Logitech")))
                .andExpect(jsonPath("$.data.description", is("Peripherals")))
                .andExpect(jsonPath("$.data.createdAt").isNumber())
                .andExpect(jsonPath("$.metadata.processTimeMs", greaterThanOrEqualTo(0)))
                .andExpect(header().exists("X-Process-Time-Ms"));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/brands").contentType("application/json")
                        .content(json(new BrandRequest("  ", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void create_nameTooLong_returns400() throws Exception {
        mockMvc.perform(post("/api/brands").contentType("application/json")
                        .content(json(new BrandRequest("x".repeat(101), null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicateName_returns409() throws Exception {
        BrandRequest request = new BrandRequest("Razer", null);
        mockMvc.perform(post("/api/brands").contentType("application/json").content(json(request)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/brands").contentType("application/json").content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void list_returnsPaging() throws Exception {
        newBrand("Asus");
        newBrand("Acer");
        mockMvc.perform(get("/api/brands").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name", is("Acer")))
                .andExpect(jsonPath("$.paging.totalElement", is(2)))
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
    }

    @Test
    void getDetail_returns200() throws Exception {
        Brand saved = newBrand("Corsair");
        mockMvc.perform(get("/api/brands/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(saved.getId().toString())))
                .andExpect(jsonPath("$.data.name", is("Corsair")));
    }

    @Test
    void getDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/brands/{id}", MISSING_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void update_returns200_withNewData() throws Exception {
        Brand saved = newBrand("OldBrand");
        mockMvc.perform(put("/api/brands/{id}", saved.getId()).contentType("application/json")
                        .content(json(new BrandRequest("NewBrand", "updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("NewBrand")))
                .andExpect(jsonPath("$.data.description", is("updated")));
    }

    @Test
    void update_duplicateName_returns409() throws Exception {
        newBrand("Alpha");
        Brand beta = newBrand("Beta");
        mockMvc.perform(put("/api/brands/{id}", beta.getId()).contentType("application/json")
                        .content(json(new BrandRequest("Alpha", null))))
                .andExpect(status().isConflict());
    }

    @Test
    void update_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/brands/{id}", MISSING_ID).contentType("application/json")
                        .content(json(new BrandRequest("Whatever", null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns200_withNullDataAndMetadata() throws Exception {
        Brand saved = newBrand("Removable");
        mockMvc.perform(delete("/api/brands/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
        mockMvc.perform(get("/api/brands/{id}", saved.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/brands/{id}", MISSING_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_referencedByProduct_returns409() throws Exception {
        Category category = newCategory("Cat");
        Brand brand = newBrand("UsedBrand");
        newProduct("SKU-B", "Item", new BigDecimal("1000"), 1, category, brand);

        mockMvc.perform(delete("/api/brands/{id}", brand.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }
}
