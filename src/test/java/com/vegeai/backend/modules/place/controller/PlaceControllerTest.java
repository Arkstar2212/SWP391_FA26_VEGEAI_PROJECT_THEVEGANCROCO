package com.vegeai.backend.modules.place.controller;

import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.dto.PlaceResponse;
import com.vegeai.backend.modules.place.service.PlaceProvider;
import com.vegeai.backend.modules.place.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OpenStreetMap Places API tests")
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlaceProvider placeProvider;

    private final List<PlaceResponse> places = List.of(
            place("node/1", "Cơm Chay Mandala", PlaceCategory.RESTAURANT, true),
            place("way/2", "Siêu Thị Chay", PlaceCategory.SUPERMARKET, false),
            place("node/3", "Cửa Hàng Thực Phẩm Chay", PlaceCategory.GROCERY_STORE, false));

    @BeforeEach
    void setUpProviderResponses() {
        when(placeProvider.findNearbyVegetarianPlaces(anyDouble(), anyDouble(), anyInt(),
                any(PlaceCategory.class), nullable(String.class)))
                .thenAnswer(invocation -> filter(invocation.getArgument(3), invocation.getArgument(4)));
        when(placeProvider.searchVegetarianPlaces(anyString(), nullable(Double.class), nullable(Double.class),
                anyInt(), any(PlaceCategory.class)))
                .thenAnswer(invocation -> filter(invocation.getArgument(4), null));
        when(placeProvider.getCategoryMetadata()).thenReturn(List.of());
    }

    @Test
    @DisplayName("Nearby returns OSM place fields and map link")
    void getNearbyPlaces_returnsPlaces() throws Exception {
        mockMvc.perform(get("/api/places/nearby")
                        .param("lat", "10.7769")
                        .param("lng", "106.7009")
                        .param("radius", "5000")
                        .param("category", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].placeId", is("node/1")))
                .andExpect(jsonPath("$.data[0].openStreetMapUrl", containsString("openstreetmap.org")))
                .andExpect(jsonPath("$.data[0].dataSource", is("OPENSTREETMAP")))
                .andExpect(jsonPath("$.data[0].attribution", containsString("OpenStreetMap contributors")))
                .andExpect(jsonPath("$.data[0].rating").doesNotExist())
                .andExpect(jsonPath("$.data[0].isOpenNow").doesNotExist());
    }

    @Test
    @DisplayName("Nearby category filter returns only the requested category")
    void getNearbyPlaces_filtersByCategory() throws Exception {
        mockMvc.perform(get("/api/places/nearby")
                        .param("lat", "10.7769")
                        .param("lng", "106.7009")
                        .param("category", "RESTAURANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].category", is("RESTAURANT")));
    }

    @Test
    @DisplayName("Search supports category filtering")
    void searchPlaces_filtersByCategory() throws Exception {
        mockMvc.perform(get("/api/places/search")
                        .param("query", "Quận 1")
                        .param("lat", "10.7769")
                        .param("lng", "106.7009")
                        .param("category", "RESTAURANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].category", is("RESTAURANT")));
    }

    @Test
    @DisplayName("Unit test GeoUtils: computes Haversine distance and formats it")
    void geoUtils_computesDistance() {
        double distance = GeoUtils.calculateHaversineDistanceKm(10.7725, 106.6980, 10.7716, 106.7044);
        assertTrue(distance > 0.5 && distance < 1.0);
        assertEquals("700 m", GeoUtils.formatDistanceText(0.70));
        assertEquals("2.5 km", GeoUtils.formatDistanceText(2.52));
    }

    private List<PlaceResponse> filter(PlaceCategory requestedCategory, String keyword) {
        return places.stream()
                .filter(place -> requestedCategory == PlaceCategory.ALL || requestedCategory == place.getCategory())
                .filter(place -> keyword == null || place.getName().toLowerCase().contains(keyword.toLowerCase()))
                .toList();
    }

    private static PlaceResponse place(String id, String name, PlaceCategory category, boolean verified) {
        return PlaceResponse.builder()
                .placeId(id)
                .name(name)
                .category(category)
                .address("Ho Chi Minh City")
                .latitude(10.7769)
                .longitude(106.7009)
                .distanceKm(0.5)
                .distanceText("500 m")
                .openStreetMapUrl("https://www.openstreetmap.org/" + id)
                .types(List.of("cuisine=vegetarian"))
                .isVerified(verified)
                .dataSource("OPENSTREETMAP")
                .attribution("© OpenStreetMap contributors")
                .build();
    }
}
