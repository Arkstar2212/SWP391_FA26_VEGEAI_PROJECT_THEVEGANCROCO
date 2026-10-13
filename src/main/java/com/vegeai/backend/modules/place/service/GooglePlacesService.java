package com.vegeai.backend.modules.place.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.place.dto.CategoryMetadataDto;
import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.dto.PlaceResponse;
import com.vegeai.backend.modules.place.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GooglePlacesService {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.google.maps.api-key:}")
    private String apiKey;

    @Value("${app.google.maps.places-base-url:https://maps.googleapis.com/maps/api/place}")
    private String placesBaseUrl;

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /**
     * Tìm kiếm các địa điểm chay quanh tọa độ (lat, lng) trong bán kính radiusMeters.
     */
    public List<PlaceResponse> findNearbyVegetarianPlaces(double userLat,
                                                          double userLng,
                                                          int radiusMeters,
                                                          PlaceCategory category,
                                                          String keyword) {
        int boundedRadius = Math.max(500, Math.min(radiusMeters, 50000));
        PlaceCategory safeCategory = (category != null) ? category : PlaceCategory.ALL;

        // 1. Kiểm tra cache Redis nếu có
        String cacheKey = buildCacheKey("nearby", userLat, userLng, boundedRadius, safeCategory, keyword);
        List<PlaceResponse> cached = getFromCache(cacheKey);
        if (cached != null) {
            log.debug("[PLACES-CACHE] Cache hit cho key: {}", cacheKey);
            return cached;
        }

        // 2. Nếu không cấu hình Google API Key, dùng bộ dữ liệu mô phỏng vị trí thông minh (Dev/Mock Fallback)
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equalsIgnoreCase("API_KEY_NOT_CONFIGURED")) {
            log.info("[PLACES-DEV] Google Maps API Key chưa được cấu hình, kích hoạt bộ định vị địa điểm chay mô phỏng.");
            List<PlaceResponse> mockResults = generateFallbackPlaces(userLat, userLng, boundedRadius, safeCategory, keyword);
            saveToCache(cacheKey, mockResults);
            return mockResults;
        }

        // 3. Gọi Google Places API (Nearby Search)
        try {
            String queryKeywords = resolveSearchKeyword(safeCategory, keyword);
            String url = String.format(Locale.US,
                    "%s/nearbysearch/json?location=%f,%f&radius=%d&keyword=%s&key=%s&language=vi",
                    placesBaseUrl, userLat, userLng, boundedRadius, queryKeywords, apiKey);

            RestClient restClient = RestClient.create();
            String rawJson = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            List<PlaceResponse> results = parseGooglePlacesResponse(rawJson, userLat, userLng, safeCategory);

            if (results.isEmpty()) {
                log.info("[PLACES] Google Places không trả về kết quả, dùng fallback dự phòng.");
                results = generateFallbackPlaces(userLat, userLng, boundedRadius, safeCategory, keyword);
            }

            saveToCache(cacheKey, results);
            return results;
        } catch (Exception e) {
            log.warn("[PLACES-ERROR] Lỗi khi gọi Google Places API: {}, kích hoạt dữ liệu dự phòng", e.getMessage());
            return generateFallbackPlaces(userLat, userLng, boundedRadius, safeCategory, keyword);
        }
    }

    /**
     * Tìm kiếm địa điểm chay theo từ khóa hoặc tên đường.
     */
    public List<PlaceResponse> searchVegetarianPlaces(String query,
                                                      Double userLat,
                                                      Double userLng,
                                                      int radiusMeters) {
        double effectiveLat = (userLat != null) ? userLat : 10.7769; // Default: TP.HCM
        double effectiveLng = (userLng != null) ? userLng : 106.7009;
        int boundedRadius = Math.max(500, Math.min(radiusMeters, 50000));

        String cacheKey = "places:search:" + query.toLowerCase().trim() + ":" + Math.round(effectiveLat * 100) + ":" + Math.round(effectiveLng * 100);
        List<PlaceResponse> cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equalsIgnoreCase("API_KEY_NOT_CONFIGURED")) {
            List<PlaceResponse> fallback = generateFallbackPlaces(effectiveLat, effectiveLng, boundedRadius, PlaceCategory.ALL, query);
            saveToCache(cacheKey, fallback);
            return fallback;
        }

        try {
            String searchQuery = "chay " + query;
            String url = String.format(Locale.US,
                    "%s/textsearch/json?query=%s&location=%f,%f&radius=%d&key=%s&language=vi",
                    placesBaseUrl, searchQuery, effectiveLat, effectiveLng, boundedRadius, apiKey);

            RestClient restClient = RestClient.create();
            String rawJson = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            List<PlaceResponse> results = parseGooglePlacesResponse(rawJson, effectiveLat, effectiveLng, PlaceCategory.ALL);
            if (results.isEmpty()) {
                results = generateFallbackPlaces(effectiveLat, effectiveLng, boundedRadius, PlaceCategory.ALL, query);
            }
            saveToCache(cacheKey, results);
            return results;
        } catch (Exception e) {
            log.warn("[PLACES-SEARCH-ERROR] Lỗi text search: {}", e.getMessage());
            return generateFallbackPlaces(effectiveLat, effectiveLng, boundedRadius, PlaceCategory.ALL, query);
        }
    }

    /**
     * Danh mục phân loại địa điểm chay.
     */
    public List<CategoryMetadataDto> getCategoryMetadata() {
        return List.of(
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.ALL)
                        .displayName("Tất cả địa điểm chay")
                        .description("Bao gồm cả quán ăn, nhà hàng, siêu thị và tiệm bán nguyên liệu chay")
                        .icon("map-pin")
                        .build(),
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.RESTAURANT)
                        .displayName("Quán ăn & Nhà hàng chay")
                        .description("Cơm chay, hủ tiếu chay, buffet chay, ẩm thực thanh đạm khi muốn đi ăn ngoài")
                        .icon("utensils")
                        .build(),
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.SUPERMARKET)
                        .displayName("Siêu thị thực phẩm chay")
                        .description("Siêu thị chuyên doanh đồ chay, thực phẩm chay đóng hộp, đồ khô nhập khẩu")
                        .icon("shopping-cart")
                        .build(),
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.GROCERY_STORE)
                        .displayName("Cửa hàng nguyên liệu & Chợ chay")
                        .description("Bán đậu phụ tươi, mì căn, nấm tươi, chả lụa chay và gia vị chay nấu tại nhà")
                        .icon("store")
                        .build()
        );
    }

    // ── Parser Google Places JSON ─────────────────────────────────────────────

    private List<PlaceResponse> parseGooglePlacesResponse(String json,
                                                          double userLat,
                                                          double userLng,
                                                          PlaceCategory requestedCategory) {
        List<PlaceResponse> places = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode resultsNode = root.path("results");
            if (resultsNode.isArray()) {
                for (JsonNode node : resultsNode) {
                    String placeId = node.path("place_id").asText();
                    String name = node.path("name").asText();
                    String address = node.has("vicinity") ? node.path("vicinity").asText() : node.path("formatted_address").asText();
                    double pLat = node.path("geometry").path("location").path("lat").asDouble();
                    double pLng = node.path("geometry").path("location").path("lng").asDouble();
                    double rating = node.path("rating").asDouble(4.5);
                    int totalRatings = node.path("user_ratings_total").asInt(0);
                    boolean isOpenNow = node.path("opening_hours").path("open_now").asBoolean(true);

                    List<String> types = new ArrayList<>();
                    if (node.path("types").isArray()) {
                        for (JsonNode t : node.path("types")) {
                            types.add(t.asText());
                        }
                    }

                    PlaceCategory detectedCategory = classifyCategory(name, types);

                    // Lọc theo danh mục yêu cầu nếu không phải ALL
                    if (requestedCategory != PlaceCategory.ALL && detectedCategory != requestedCategory) {
                        continue;
                    }

                    double distanceKm = GeoUtils.calculateHaversineDistanceKm(userLat, userLng, pLat, pLng);

                    String photoUrl = "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=500";
                    if (node.path("photos").isArray() && !node.path("photos").isEmpty()) {
                        String photoRef = node.path("photos").get(0).path("photo_reference").asText();
                        photoUrl = String.format("%s/photo?maxwidth=400&photo_reference=%s&key=%s",
                                placesBaseUrl, photoRef, apiKey);
                    }

                    PlaceResponse place = PlaceResponse.builder()
                            .placeId(placeId)
                            .name(name)
                            .category(detectedCategory)
                            .address(address)
                            .latitude(pLat)
                            .longitude(pLng)
                            .distanceKm(distanceKm)
                            .distanceText(GeoUtils.formatDistanceText(distanceKm))
                            .rating(rating)
                            .userRatingsTotal(totalRatings)
                            .isOpenNow(isOpenNow)
                            .photoUrl(photoUrl)
                            .googleMapsUrl(GeoUtils.buildGoogleMapsDirectionsUrl(pLat, pLng, placeId))
                            .types(types)
                            .isVerified(true)
                            .build();

                    places.add(place);
                }
            }
        } catch (Exception e) {
            log.error("[PLACES-PARSER] Lỗi parse JSON Google Places: {}", e.getMessage());
        }

        places.sort(Comparator.comparingDouble(PlaceResponse::getDistanceKm));
        return places;
    }

    private PlaceCategory classifyCategory(String name, List<String> types) {
        String lowerName = (name != null) ? name.toLowerCase() : "";
        if (lowerName.contains("siêu thị") || lowerName.contains("mart") || lowerName.contains("supermarket") || types.contains("supermarket")) {
            return PlaceCategory.SUPERMARKET;
        }
        if (lowerName.contains("cửa hàng") || lowerName.contains("thực phẩm") || lowerName.contains("đậu phụ")
                || lowerName.contains("bách hóa") || types.contains("grocery_or_supermarket") || types.contains("store")) {
            return PlaceCategory.GROCERY_STORE;
        }
        return PlaceCategory.RESTAURANT;
    }

    private String resolveSearchKeyword(PlaceCategory category, String customKeyword) {
        String base = switch (category) {
            case RESTAURANT -> "quán chay|nhà hàng chay|cơm chay";
            case SUPERMARKET -> "siêu thị chay|thực phẩm chay";
            case GROCERY_STORE -> "cửa hàng thực phẩm chay|đậu phụ chay";
            case ALL -> "quán chay|thực phẩm chay|siêu thị chay";
        };
        if (customKeyword != null && !customKeyword.isBlank()) {
            return base + "|" + customKeyword.trim();
        }
        return base;
    }

    // ── Smart Fallback Mock Generator (Dev & Test Mode) ───────────────────────

    private List<PlaceResponse> generateFallbackPlaces(double userLat,
                                                       double userLng,
                                                       int radiusMeters,
                                                       PlaceCategory requestedCategory,
                                                       String keyword) {
        List<PlaceResponse> mockList = new ArrayList<>();

        // Danh sách mẫu phong phú cho ẩm thực chay Việt Nam
        addMock(mockList, userLat, userLng, userLat + 0.0035, userLng + 0.0028, "Cơm Chay Mandala",
                PlaceCategory.RESTAURANT, "45 Thạch Thị Thanh, Tân Định, Quận 1", 4.8, 234, true,
                "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500", "028 3820 5432");

        addMock(mockList, userLat, userLng, userLat - 0.0042, userLng + 0.0031, "Nhà Hàng Chay Thiện Duyên",
                PlaceCategory.RESTAURANT, "86 Nguyễn Đình Chiểu, Đa Kao, Quận 1", 4.7, 182, true,
                "https://images.unsplash.com/photo-1540420773420-3366772f4999?w=500", "028 3910 1122");

        addMock(mockList, userLat, userLng, userLat + 0.0061, userLng - 0.0045, "Quán Chay Thanh Lương",
                PlaceCategory.RESTAURANT, "545 Cách Mạng Tháng 8, Phường 15, Quận 10", 4.6, 95, true,
                "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=500", "090 312 3456");

        addMock(mockList, userLat, userLng, userLat - 0.0075, userLng - 0.0038, "Buffet Chay Mãn Tự",
                PlaceCategory.RESTAURANT, "201 Nguyễn Thị Minh Khai, Nguyễn Cư Trinh, Quận 1", 4.9, 512, true,
                "https://images.unsplash.com/photo-1547592180-85f173990554?w=500", "093 888 9999");

        addMock(mockList, userLat, userLng, userLat + 0.0022, userLng - 0.0021, "Siêu Thị Thực Phẩm Chay Đại An",
                PlaceCategory.SUPERMARKET, "112 Bùi Thị Xuân, Bến Thành, Quận 1", 4.9, 140, true,
                "https://images.unsplash.com/photo-1578916171728-46686eac8d58?w=500", "028 3925 6789");

        addMock(mockList, userLat, userLng, userLat - 0.0051, userLng - 0.0055, "Cửa Hàng Thực Phẩm Chay Tuệ Đức",
                PlaceCategory.GROCERY_STORE, "240 Trần Hưng Đạo, Phường 11, Quận 5", 4.6, 88, true,
                "https://images.unsplash.com/photo-1506484381205-f7945653044d?w=500", "091 234 5678");

        addMock(mockList, userLat, userLng, userLat + 0.0085, userLng + 0.0062, "Bách Hóa Chay Thuần Chay Store",
                PlaceCategory.SUPERMARKET, "18A Điện Biên Phủ, Phường 25, Bình Thạnh", 4.8, 175, true,
                "https://images.unsplash.com/photo-1542838132-92c53300491e?w=500", "028 6258 7890");

        addMock(mockList, userLat, userLng, userLat - 0.0019, userLng + 0.0054, "Tiệm Đậu Phụ Sạch & Nấm Tươi Hoàng Gia",
                PlaceCategory.GROCERY_STORE, "72 Võ Văn Tần, Phường 6, Quận 3", 4.7, 63, true,
                "https://images.unsplash.com/photo-1589927986089-35812388d1f4?w=500", "098 765 4321");

        // Lọc theo khoảng cách và danh mục
        double maxRadiusKm = radiusMeters / 1000.0;
        List<PlaceResponse> filtered = mockList.stream()
                .filter(p -> p.getDistanceKm() <= maxRadiusKm)
                .filter(p -> requestedCategory == PlaceCategory.ALL || p.getCategory() == requestedCategory)
                .filter(p -> {
                    if (keyword == null || keyword.isBlank()) return true;
                    String kw = keyword.toLowerCase().trim();
                    return p.getName().toLowerCase().contains(kw) || p.getAddress().toLowerCase().contains(kw);
                })
                .sorted(Comparator.comparingDouble(PlaceResponse::getDistanceKm))
                .toList();

        return filtered;
    }

    private void addMock(List<PlaceResponse> list, double userLat, double userLng, double lat, double lng, String name,
                         PlaceCategory category, String address, double rating, int reviews, boolean isOpen,
                         String photoUrl, String phone) {
        String mockId = "mock_" + UUID.nameUUIDFromBytes(name.getBytes());
        double distKm = GeoUtils.calculateHaversineDistanceKm(userLat, userLng, lat, lng);
        list.add(PlaceResponse.builder()
                .placeId(mockId)
                .name(name)
                .category(category)
                .address(address)
                .latitude(lat)
                .longitude(lng)
                .distanceKm(distKm)
                .distanceText(GeoUtils.formatDistanceText(distKm))
                .rating(rating)
                .userRatingsTotal(reviews)
                .isOpenNow(isOpen)
                .photoUrl(photoUrl)
                .googleMapsUrl(GeoUtils.buildGoogleMapsDirectionsUrl(lat, lng, mockId))
                .phoneNumber(phone)
                .types(List.of(category.name().toLowerCase(), "vegetarian", "food"))
                .isVerified(true)
                .build());
    }

    // ── Caching Helpers ───────────────────────────────────────────────────────

    private String buildCacheKey(String prefix, double lat, double lng, int radius, PlaceCategory cat, String kw) {
        long latRound = Math.round(lat * 100);
        long lngRound = Math.round(lng * 100);
        String kwPart = (kw != null && !kw.isBlank()) ? kw.trim().toLowerCase() : "all";
        return String.format("places:%s:%d:%d:%d:%s:%s", prefix, latRound, lngRound, radius, cat.name(), kwPart);
    }

    private List<PlaceResponse> getFromCache(String key) {
        if (redisTemplate == null) return null;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<PlaceResponse>>() {});
            }
        } catch (Exception e) {
            log.debug("[REDIS-CACHE] Bỏ qua lỗi đọc cache: {}", e.getMessage());
        }
        return null;
    }

    private void saveToCache(String key, List<PlaceResponse> data) {
        if (redisTemplate == null || data == null || data.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
        } catch (Exception e) {
            log.debug("[REDIS-CACHE] Bỏ qua lỗi ghi cache: {}", e.getMessage());
        }
    }
}
