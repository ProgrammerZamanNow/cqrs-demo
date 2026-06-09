package com.pzn.product.product;

import com.pzn.product.AbstractIntegrationTest;
import com.pzn.product.brand.Brand;
import com.pzn.product.category.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dataset (keyword "sepatu" cocok untuk semua):
 * <pre>
 * sku name                  category  brand   price   stock
 * P1  sepatu lari nike      Shoes     Nike     50000   5  (<100k, in)
 * P2  sepatu basket nike    Shoes     Nike    300000   0  (100-500k, out)
 * P3  sepatu bola adidas    Shoes     Adidas  250000   5  (100-500k, in)
 * P4  sepatu futsal adidas  Sandals   Adidas  700000   2  (>500k, in)
 * P5  sepatu gunung adidas  Sandals   Adidas   90000   0  (<100k, out)
 * </pre>
 */
class ProductSearchApiTest extends AbstractIntegrationTest {

    private Category shoes;
    private Category sandals;
    private Brand nike;
    private Brand adidas;

    @BeforeEach
    void seed() {
        shoes = newCategory("Shoes");
        sandals = newCategory("Sandals");
        nike = newBrand("Nike");
        adidas = newBrand("Adidas");

        newProduct("P1", "sepatu lari nike", new BigDecimal("50000"), 5, shoes, nike);
        newProduct("P2", "sepatu basket nike", new BigDecimal("300000"), 0, shoes, nike);
        newProduct("P3", "sepatu bola adidas", new BigDecimal("250000"), 5, shoes, adidas);
        newProduct("P4", "sepatu futsal adidas", new BigDecimal("700000"), 2, sandals, adidas);
        newProduct("P5", "sepatu gunung adidas", new BigDecimal("90000"), 0, sandals, adidas);
    }

    @Test
    void search_noExtraFilter_returnsAllWithFullFacets() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "sepatu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.paging.totalElement", is(5)))
                // categories
                .andExpect(jsonPath("$.facets.categories[?(@.name=='Shoes')].count", contains(3)))
                .andExpect(jsonPath("$.facets.categories[?(@.name=='Sandals')].count", contains(2)))
                // brands
                .andExpect(jsonPath("$.facets.brands[?(@.name=='Nike')].count", contains(2)))
                .andExpect(jsonPath("$.facets.brands[?(@.name=='Adidas')].count", contains(3)))
                // price buckets (fixed order: <100k, 100-500k, >500k)
                .andExpect(jsonPath("$.facets.priceRanges", hasSize(3)))
                .andExpect(jsonPath("$.facets.priceRanges[0].count", is(2)))
                .andExpect(jsonPath("$.facets.priceRanges[1].count", is(2)))
                .andExpect(jsonPath("$.facets.priceRanges[2].count", is(1)))
                // availability
                .andExpect(jsonPath("$.facets.availability[?(@.value=='IN_STOCK')].count", contains(3)))
                .andExpect(jsonPath("$.facets.availability[?(@.value=='OUT_OF_STOCK')].count", contains(2)))
                .andExpect(jsonPath("$.metadata.processTimeMs").isNumber());
    }

    @Test
    void search_keyword_filtersByName() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "basket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].sku", is("P2")));
    }

    @Test
    void search_categoryFilter_marksSelected() throws Exception {
        mockMvc.perform(get("/api/products").param("categoryId", shoes.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.facets.categories[?(@.name=='Shoes')].selected", contains(true)));
    }

    @Test
    void search_brandFilter_drillDownKeepsOtherBrands() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("keyword", "sepatu")
                        .param("brandId", nike.getId().toString()))
                .andExpect(status().isOk())
                // data hanya Nike (P1, P2)
                .andExpect(jsonPath("$.data", hasSize(2)))
                // brand facet TETAP memuat Nike & Adidas (drill-down: abaikan filter brand)
                .andExpect(jsonPath("$.facets.brands", hasSize(2)))
                .andExpect(jsonPath("$.facets.brands[?(@.name=='Adidas')].count", contains(3)))
                .andExpect(jsonPath("$.facets.brands[?(@.name=='Nike')].count", contains(2)))
                .andExpect(jsonPath("$.facets.brands[?(@.name=='Nike')].selected", contains(true)))
                // category facet IKUT filter brand: Nike hanya di Shoes -> Shoes=2, Sandals hilang
                .andExpect(jsonPath("$.facets.categories", hasSize(1)))
                .andExpect(jsonPath("$.facets.categories[?(@.name=='Shoes')].count", contains(2)));
    }

    @Test
    void search_multipleBrandIds_orCombined() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("brandId", nike.getId().toString())
                        .param("brandId", adidas.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));
    }

    @Test
    void search_priceRangeFilter() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("minPrice", "100000")
                        .param("maxPrice", "500000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void search_availabilityFilter_inStock() throws Exception {
        mockMvc.perform(get("/api/products").param("availability", "IN_STOCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.facets.availability[?(@.value=='IN_STOCK')].selected", contains(true)));
    }

    @Test
    void search_emptyResult_returnsEmptyFacets() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "zzz-nomatch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.facets.categories", hasSize(0)))
                .andExpect(jsonPath("$.facets.brands", hasSize(0)))
                .andExpect(jsonPath("$.facets.priceRanges", hasSize(0)))
                .andExpect(jsonPath("$.facets.availability", hasSize(0)));
    }

    @Test
    void search_sortByPriceAsc() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sku", is("P1")));
    }

    @Test
    void search_sortByPriceDesc() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sku", is("P4")));
    }

    @Test
    void search_invalidSortField_ignoredNoError() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "unknownField,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));
    }

    @Test
    void search_pagination() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.paging.size", is(2)))
                .andExpect(jsonPath("$.paging.totalElement", is(5)))
                .andExpect(jsonPath("$.paging.totalPage", is(3)));
    }

    @Test
    void search_pageOutOfRange_returnsEmptyData() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "10").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.paging.totalElement", is(5)));
    }

    @Test
    void search_sizeOver100_returns400() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
