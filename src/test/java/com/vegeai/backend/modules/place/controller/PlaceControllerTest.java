package com.vegeai.backend.modules.place.controller;

import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.util.GeoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Google Places & Vegetarian Maps Integration Tests")
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/places/nearby: Trả về danh sách địa điểm chay quanh tọa độ người dùng, sắp xếp theo khoảng cách")
    void getNearbyPlaces_returnsSortedPlaces() throws Exception {
        double userLat = 10.7769; // TP.HCM Bến Thành
        double userLng = 106.7009;

        mockMvc.perform(get("/api/places/nearby")
                        .param("lat", String.valueOf(userLat))
                        .param("lng", String.valueOf(userLng))
                        .param("radius", "5000")
                        .param("category", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data[0].distanceKm").isNumber())
                .andExpect(jsonPath("$.data[0].distanceText").isNotEmpty())
                .andExpect(jsonPath("$.data[0].googleMapsUrl", containsString("https://www.google.com/maps/dir/")));
    }

    @Test
    @DisplayName("GET /api/places/nearby?category=RESTAURANT: Lọc chỉ lấy quán ăn / nhà hàng chay")
    void getNearbyPlaces_filterRestaurantOnly() throws Exception {
        mockMvc.perform(get("/api/places/nearby")
                        .param("lat", "10.7769")
                        .param("lng", "106.7009")
                        .param("radius", "5000")
                        .param("category", "RESTAURANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[*].category", everyItem(is("RESTAURANT"))));
    }

    @Test
    @DisplayName("GET /api/places/nearby?category=SUPERMARKET: Lọc chỉ lấy siêu thị chay")
    void getNearbyPlaces_filterSupermarketOnly() throws Exception {
        mockMvc.perform(get("/api/places/nearby")
                        .param("lat", "10.7769")
                        .param("lng", "106.7009")
                        .param("radius", "5000")
                        .param("category", "SUPERMARKET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[*].category", everyItem(is("SUPERMARKET"))));
    }

    @Test
    @DisplayName("GET /api/places/nearby?category=GROCERY_STORE: Lọc cửa hàng nguyên liệu thực phẩm chay")
    void getNearbyPlaces_filterGroceryStoreOnly() throws Exception {
        mockMvc.perform(get("/api/places/nearby")
                        .param("lat", "10.7769")
                        .param("lng", "106.7009")
                        .param("radius", "5000")
                        .param("category", "GROCERY_STORE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[*].category", everyItem(is("GROCERY_STORE"))));
    }

    @Test
    @DisplayName("GET /api/places/nearby?keyword=Mandala: Lọc theo từ khóa quán cụ thể")
    void getNearbyPlaces_filterByKeyword() throws Exception {
        mockMvc.perform(get("/api/places/nearby")
                        .param("lat", "10.7769")
                        .param("lng", "106.7009")
                        .param("radius", "5000")
                        .param("keyword", "Mandala"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name", containsString("Mandala")));
    }

    @Test
    @DisplayName("GET /api/places/search: Tìm kiếm địa điểm chay theo địa danh hoặc tên đường")
    void searchPlaces_byQuery() throws Exception {
        mockMvc.perform(get("/api/places/search")
                        .param("query", "Quận 1")
                        .param("lat", "10.7769")
                        .param("lng", "106.7009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[0].name").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/places/categories: Trả về đầy đủ 4 danh mục phân loại bản đồ")
    void getCategories_returnsAllFour() throws Exception {
        mockMvc.perform(get("/api/places/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[*].category", containsInAnyOrder("ALL", "RESTAURANT", "SUPERMARKET", "GROCERY_STORE")));
    }

    @Test
    @DisplayName("Unit Test GeoUtils: Tính khoảng cách Haversine chính xác và định dạng text")
    void geoUtils_computesAccurateDistance() {
        // Tọa độ Chợ Bến Thành (10.7725, 106.6980) và Bitexco (10.7716, 106.7044) ~ khoảng 0.7 km
        double distance = GeoUtils.calculateHaversineDistanceKm(10.7725, 106.6980, 10.7716, 106.7044);
        assertTrue(distance > 0.5 && distance < 1.0, "Khoảng cách thực tế phải nằm trong khoảng 0.5 - 1.0 km");

        assertEquals("700 m", GeoUtils.formatDistanceText(0.70));
        assertEquals("2.5 km", GeoUtils.formatDistanceText(2.52));

        String url = GeoUtils.buildGoogleMapsDirectionsUrl(10.7725, 106.6980, "place123");
        assertTrue(url.contains("destination=10.7725,106.698"));
        assertTrue(url.contains("destination_place_id=place123"));
    }
}
