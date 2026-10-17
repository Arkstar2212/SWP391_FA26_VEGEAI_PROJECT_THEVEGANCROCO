package com.vegeai.backend.modules.place.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.place.dto.CategoryMetadataDto;
import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.dto.PlaceInputRequest;
import com.vegeai.backend.modules.place.dto.PlaceResponse;
import com.vegeai.backend.modules.place.entity.MapPlace;
import com.vegeai.backend.modules.place.repository.MapPlaceRepository;
import com.vegeai.backend.modules.place.util.GeoUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GooglePlacesService {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final MapPlaceRepository mapPlaceRepository;

    @Value("${app.google.maps.api-key:}")
    private String apiKey;

    @Value("${app.google.maps.places-base-url:https://maps.googleapis.com/maps/api/place}")
    private String placesBaseUrl;

    private static final Duration CACHE_TTL = Duration.ofHours(24);

    @PostConstruct
    @Transactional
    public void initDefaultPlaces() {
        try {
            ensureDefaultPlacesSeeded();
        } catch (Exception e) {
            log.warn("[PLACES-INIT] Bỏ qua seed mặc định khi khởi tạo: {}", e.getMessage());
        }
    }

    @Transactional
    public void ensureDefaultPlacesSeeded() {
        if (mapPlaceRepository.count() > 0) {
            return;
        }
        List<MapPlace> seeds = List.of(
                // Quán ăn & Nhà hàng chay
                MapPlace.builder()
                        .placeId("place_mandala_q1")
                        .name("Cơm Chay Mandala")
                        .category(PlaceCategory.RESTAURANT)
                        .address("45 Thạch Thị Thanh, Tân Định, Quận 1, TP. Hồ Chí Minh")
                        .latitude(10.7804)
                        .longitude(106.7037)
                        .rating(4.8)
                        .userRatingsTotal(234)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500")
                        .phoneNumber("028 3820 5432")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_thienduyen_q1")
                        .name("Nhà Hàng Chay Thiện Duyên")
                        .category(PlaceCategory.RESTAURANT)
                        .address("86 Nguyễn Đình Chiểu, Đa Kao, Quận 1, TP. Hồ Chí Minh")
                        .latitude(10.7727)
                        .longitude(106.7040)
                        .rating(4.7)
                        .userRatingsTotal(182)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1540420773420-3366772f4999?w=500")
                        .phoneNumber("028 3910 1122")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_thanhluong_q10")
                        .name("Quán Chay Thanh Lương")
                        .category(PlaceCategory.RESTAURANT)
                        .address("545 Cách Mạng Tháng 8, Phường 15, Quận 10, TP. Hồ Chí Minh")
                        .latitude(10.7830)
                        .longitude(106.6964)
                        .rating(4.6)
                        .userRatingsTotal(95)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=500")
                        .phoneNumber("090 312 3456")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_mantu_q1")
                        .name("Buffet Chay Mãn Tự")
                        .category(PlaceCategory.RESTAURANT)
                        .address("201 Nguyễn Thị Minh Khai, Nguyễn Cư Trinh, Quận 1, TP. Hồ Chí Minh")
                        .latitude(10.7694)
                        .longitude(106.6971)
                        .rating(4.9)
                        .userRatingsTotal(512)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1547592180-85f173990554?w=500")
                        .phoneNumber("093 888 9999")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_hum_q3")
                        .name("Hum Vegetarian, Lounge & Restaurant")
                        .category(PlaceCategory.RESTAURANT)
                        .address("32 Võ Văn Tần, Phường 6, Quận 3, TP. Hồ Chí Minh")
                        .latitude(10.7765)
                        .longitude(106.6902)
                        .rating(4.9)
                        .userRatingsTotal(640)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1540420773420-3366772f4999?w=500")
                        .phoneNumber("028 3930 3838")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),

                // Siêu thị chay & Cửa hàng nguyên liệu
                MapPlace.builder()
                        .placeId("place_daian_q1")
                        .name("Siêu Thị Thực Phẩm Chay Đại An")
                        .category(PlaceCategory.SUPERMARKET)
                        .address("112 Bùi Thị Xuân, Bến Thành, Quận 1, TP. Hồ Chí Minh")
                        .latitude(10.7791)
                        .longitude(106.6988)
                        .rating(4.9)
                        .userRatingsTotal(140)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1578916171728-46686eac8d58?w=500")
                        .phoneNumber("028 3925 6789")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_thuanchay_bt")
                        .name("Bách Hóa Chay Thuần Chay Store")
                        .category(PlaceCategory.SUPERMARKET)
                        .address("18A Điện Biên Phủ, Phường 25, Bình Thạnh, TP. Hồ Chí Minh")
                        .latitude(10.7854)
                        .longitude(106.7071)
                        .rating(4.8)
                        .userRatingsTotal(175)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1542838132-92c53300491e?w=500")
                        .phoneNumber("028 6258 7890")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_tueduc_q5")
                        .name("Cửa Hàng Thực Phẩm Chay Tuệ Đức")
                        .category(PlaceCategory.GROCERY_STORE)
                        .address("240 Trần Hưng Đạo, Phường 11, Quận 5, TP. Hồ Chí Minh")
                        .latitude(10.7718)
                        .longitude(106.6954)
                        .rating(4.6)
                        .userRatingsTotal(88)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1506484381205-f7945653044d?w=500")
                        .phoneNumber("091 234 5678")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_hoanggia_q3")
                        .name("Tiệm Đậu Phụ Sạch & Nấm Tươi Hoàng Gia")
                        .category(PlaceCategory.GROCERY_STORE)
                        .address("72 Võ Văn Tần, Phường 6, Quận 3, TP. Hồ Chí Minh")
                        .latitude(10.7750)
                        .longitude(106.7063)
                        .rating(4.7)
                        .userRatingsTotal(63)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1589927986089-35812388d1f4?w=500")
                        .phoneNumber("098 765 4321")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),

                // Hệ thống Điện Máy Xanh gắn trên API Map
                MapPlace.builder()
                        .placeId("place_dmx_ntmk_q1")
                        .name("Điện Máy Xanh Nguyễn Thị Minh Khai")
                        .category(PlaceCategory.DIEN_MAY_XANH)
                        .address("189A Nguyễn Thị Minh Khai, Phường Phạm Ngũ Lão, Quận 1, TP. Hồ Chí Minh")
                        .latitude(10.7698)
                        .longitude(106.6884)
                        .rating(4.8)
                        .userRatingsTotal(420)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1556911220-bff31c812dba?w=500")
                        .phoneNumber("1900 232 461")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_dmx_cmt8_q3")
                        .name("Điện Máy Xanh Cách Mạng Tháng 8")
                        .category(PlaceCategory.DIEN_MAY_XANH)
                        .address("328 Cách Mạng Tháng 8, Phường 10, Quận 3, TP. Hồ Chí Minh")
                        .latitude(10.7782)
                        .longitude(106.6785)
                        .rating(4.7)
                        .userRatingsTotal(315)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1584622650111-993a426fbf0a?w=500")
                        .phoneNumber("1900 232 461")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_dmx_dbp_q10")
                        .name("Điện Máy Xanh Điện Biên Phủ")
                        .category(PlaceCategory.DIEN_MAY_XANH)
                        .address("458 Điện Biên Phủ, Phường 11, Quận 10, TP. Hồ Chí Minh")
                        .latitude(10.7715)
                        .longitude(106.6798)
                        .rating(4.8)
                        .userRatingsTotal(289)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=500")
                        .phoneNumber("1900 232 461")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_dmx_xvnt_bt")
                        .name("Điện Máy Xanh Xô Viết Nghệ Tĩnh")
                        .category(PlaceCategory.DIEN_MAY_XANH)
                        .address("246 Xô Viết Nghệ Tĩnh, Phường 21, Bình Thạnh, TP. Hồ Chí Minh")
                        .latitude(10.8012)
                        .longitude(106.7108)
                        .rating(4.7)
                        .userRatingsTotal(260)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1556911220-bff31c812dba?w=500")
                        .phoneNumber("1900 232 461")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_dmx_thd_q5")
                        .name("Điện Máy Xanh Trần Hưng Đạo")
                        .category(PlaceCategory.DIEN_MAY_XANH)
                        .address("542 Trần Hưng Đạo, Phường 2, Quận 5, TP. Hồ Chí Minh")
                        .latitude(10.7568)
                        .longitude(106.6782)
                        .rating(4.8)
                        .userRatingsTotal(340)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1584622650111-993a426fbf0a?w=500")
                        .phoneNumber("1900 232 461")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build(),
                MapPlace.builder()
                        .placeId("place_dmx_lvv_td")
                        .name("Điện Máy Xanh Lê Văn Việt - TP. Thủ Đức")
                        .category(PlaceCategory.DIEN_MAY_XANH)
                        .address("152 Lê Văn Việt, Phường Hiệp Phú, TP. Thủ Đức, TP. Hồ Chí Minh")
                        .latitude(10.8442)
                        .longitude(106.7815)
                        .rating(4.9)
                        .userRatingsTotal(410)
                        .isOpenNow(true)
                        .photoUrl("https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=500")
                        .phoneNumber("1900 232 461")
                        .isVerified(true)
                        .createdByAdmin(false)
                        .build()
        );
        mapPlaceRepository.saveAll(seeds);
    }

    /**
     * Lấy toàn bộ các địa điểm trên bản đồ (bao gồm quán chay, siêu thị, và Điện Máy Xanh + địa điểm Admin thêm).
     */
    @Transactional
    public List<PlaceResponse> getAllMapPlaces(PlaceCategory category, String keyword, Double userLat, Double userLng) {
        ensureDefaultPlacesSeeded();
        double refLat = (userLat != null) ? userLat : 10.7769;
        double refLng = (userLng != null) ? userLng : 106.7009;
        PlaceCategory safeCategory = (category != null) ? category : PlaceCategory.ALL;

        List<MapPlace> dbPlaces = mapPlaceRepository.findAll();
        return dbPlaces.stream()
                .filter(p -> safeCategory == PlaceCategory.ALL || p.getCategory() == safeCategory)
                .filter(p -> matchesKeyword(p.getName(), p.getAddress(), keyword))
                .map(p -> toPlaceResponse(p, refLat, refLng))
                .sorted(Comparator.comparingDouble(PlaceResponse::getDistanceKm))
                .toList();
    }

    /**
     * Admin thêm mới địa điểm lên bản đồ.
     */
    @Transactional
    public PlaceResponse createPlace(PlaceInputRequest req) {
        ensureDefaultPlacesSeeded();
        PlaceCategory cat = (req.getCategory() == null || req.getCategory() == PlaceCategory.ALL)
                ? PlaceCategory.RESTAURANT
                : req.getCategory();
        String placeId = "admin_" + UUID.randomUUID().toString().substring(0, 12);
        String defaultPhoto = cat == PlaceCategory.DIEN_MAY_XANH
                ? "https://images.unsplash.com/photo-1556911220-bff31c812dba?w=500"
                : "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=500";

        MapPlace entity = MapPlace.builder()
                .placeId(placeId)
                .name(req.getName().trim())
                .category(cat)
                .address(req.getAddress().trim())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .rating(req.getRating() != null ? req.getRating() : 4.8)
                .userRatingsTotal(req.getUserRatingsTotal() != null ? req.getUserRatingsTotal() : 50)
                .isOpenNow(req.getIsOpenNow() != null ? req.getIsOpenNow() : true)
                .photoUrl((req.getPhotoUrl() != null && !req.getPhotoUrl().isBlank()) ? req.getPhotoUrl().trim() : defaultPhoto)
                .phoneNumber(req.getPhoneNumber() != null ? req.getPhoneNumber().trim() : "")
                .isVerified(req.getIsVerified() != null ? req.getIsVerified() : true)
                .createdByAdmin(true)
                .build();

        MapPlace saved = mapPlaceRepository.saveAndFlush(entity);
        evictPlacesCache();
        return toPlaceResponse(saved, saved.getLatitude(), saved.getLongitude());
    }

    /**
     * Admin cập nhật địa điểm trên bản đồ.
     */
    @Transactional
    public PlaceResponse updatePlace(String placeId, PlaceInputRequest req) {
        ensureDefaultPlacesSeeded();
        MapPlace entity = mapPlaceRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy địa điểm với mã: " + placeId));

        PlaceCategory cat = (req.getCategory() == null || req.getCategory() == PlaceCategory.ALL)
                ? entity.getCategory()
                : req.getCategory();

        entity.setName(req.getName().trim());
        entity.setCategory(cat);
        entity.setAddress(req.getAddress().trim());
        entity.setLatitude(req.getLatitude());
        entity.setLongitude(req.getLongitude());
        if (req.getRating() != null) entity.setRating(req.getRating());
        if (req.getUserRatingsTotal() != null) entity.setUserRatingsTotal(req.getUserRatingsTotal());
        if (req.getIsOpenNow() != null) entity.setIsOpenNow(req.getIsOpenNow());
        if (req.getPhotoUrl() != null && !req.getPhotoUrl().isBlank()) entity.setPhotoUrl(req.getPhotoUrl().trim());
        if (req.getPhoneNumber() != null) entity.setPhoneNumber(req.getPhoneNumber().trim());
        if (req.getIsVerified() != null) entity.setVerified(req.getIsVerified());

        MapPlace saved = mapPlaceRepository.saveAndFlush(entity);
        evictPlacesCache();
        return toPlaceResponse(saved, saved.getLatitude(), saved.getLongitude());
    }

    /**
     * Admin xóa địa điểm khỏi bản đồ.
     */
    @Transactional
    public void deletePlace(String placeId) {
        ensureDefaultPlacesSeeded();
        MapPlace entity = mapPlaceRepository.findByPlaceId(placeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy địa điểm với mã: " + placeId));
        mapPlaceRepository.delete(entity);
        mapPlaceRepository.flush();
        evictPlacesCache();
    }

    /**
     * Tìm kiếm các địa điểm chay & Điện Máy Xanh quanh tọa độ (lat, lng) trong bán kính radiusMeters.
     */
    @Transactional
    public List<PlaceResponse> findNearbyVegetarianPlaces(double userLat,
                                                          double userLng,
                                                          int radiusMeters,
                                                          PlaceCategory category,
                                                          String keyword) {
        ensureDefaultPlacesSeeded();
        int boundedRadius = Math.max(500, Math.min(radiusMeters, 50000));
        PlaceCategory safeCategory = (category != null) ? category : PlaceCategory.ALL;

        // 1. Kiểm tra cache Redis nếu có
        String cacheKey = buildCacheKey("nearby", userLat, userLng, boundedRadius, safeCategory, keyword);
        List<PlaceResponse> cached = getFromCache(cacheKey);
        if (cached != null) {
            log.debug("[PLACES-CACHE] Cache hit cho key: {}", cacheKey);
            return cached;
        }

        // 2. Nếu không cấu hình Google API Key, kết hợp dữ liệu DB + mô phỏng vị trí thông minh
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equalsIgnoreCase("API_KEY_NOT_CONFIGURED")) {
            log.info("[PLACES-DEV] Sử dụng cơ sở dữ liệu bản đồ nội bộ & định vị địa điểm.");
            List<PlaceResponse> mockResults = generateFallbackPlaces(userLat, userLng, boundedRadius, safeCategory, keyword);
            saveToCache(cacheKey, mockResults);
            return mockResults;
        }

        // 3. Gọi Google Places API (Nearby Search) kết hợp với các địa điểm DB của hệ thống
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

            List<PlaceResponse> results = new ArrayList<>(parseGooglePlacesResponse(rawJson, userLat, userLng, safeCategory));
            List<PlaceResponse> dbMatches = findDbPlacesWithinRadius(userLat, userLng, boundedRadius, safeCategory, keyword);
            mergeUniquePlaces(results, dbMatches);

            if (results.isEmpty()) {
                log.info("[PLACES] Google Places không trả về kết quả, dùng fallback dự phòng.");
                results = generateFallbackPlaces(userLat, userLng, boundedRadius, safeCategory, keyword);
            }

            results.sort(Comparator.comparingDouble(PlaceResponse::getDistanceKm));
            saveToCache(cacheKey, results);
            return results;
        } catch (Exception e) {
            log.warn("[PLACES-ERROR] Lỗi khi gọi Google Places API: {}, kích hoạt dữ liệu dự phòng", e.getMessage());
            return generateFallbackPlaces(userLat, userLng, boundedRadius, safeCategory, keyword);
        }
    }

    /**
     * Tìm kiếm địa điểm chay hoặc Điện Máy Xanh theo từ khóa hoặc tên đường.
     */
    @Transactional
    public List<PlaceResponse> searchVegetarianPlaces(String query,
                                                      Double userLat,
                                                      Double userLng,
                                                      int radiusMeters) {
        ensureDefaultPlacesSeeded();
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
            String searchQuery = query.toLowerCase().contains("điện máy") ? query : "chay " + query;
            String url = String.format(Locale.US,
                    "%s/textsearch/json?query=%s&location=%f,%f&radius=%d&key=%s&language=vi",
                    placesBaseUrl, searchQuery, effectiveLat, effectiveLng, boundedRadius, apiKey);

            RestClient restClient = RestClient.create();
            String rawJson = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            List<PlaceResponse> results = new ArrayList<>(parseGooglePlacesResponse(rawJson, effectiveLat, effectiveLng, PlaceCategory.ALL));
            List<PlaceResponse> dbMatches = findDbPlacesWithinRadius(effectiveLat, effectiveLng, 50000, PlaceCategory.ALL, query);
            mergeUniquePlaces(results, dbMatches);

            if (results.isEmpty()) {
                results = generateFallbackPlaces(effectiveLat, effectiveLng, boundedRadius, PlaceCategory.ALL, query);
            }
            results.sort(Comparator.comparingDouble(PlaceResponse::getDistanceKm));
            saveToCache(cacheKey, results);
            return results;
        } catch (Exception e) {
            log.warn("[PLACES-SEARCH-ERROR] Lỗi text search: {}", e.getMessage());
            return generateFallbackPlaces(effectiveLat, effectiveLng, boundedRadius, PlaceCategory.ALL, query);
        }
    }

    /**
     * Danh mục phân loại địa điểm trên bản đồ (Bao gồm Quán chay, Siêu thị, Nguyên liệu và Điện Máy Xanh).
     */
    public List<CategoryMetadataDto> getCategoryMetadata() {
        return List.of(
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.ALL)
                        .displayName("Tất cả địa điểm")
                        .description("Bao gồm quán ăn chay, siêu thị chay, cửa hàng nguyên liệu và hệ thống Điện Máy Xanh")
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
                        .build(),
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.DIEN_MAY_XANH)
                        .displayName("Điện Máy Xanh & Thiết bị bếp")
                        .description("Hệ thống siêu thị Điện Máy Xanh cung cấp nồi chiên, máy xay, tủ lạnh & thiết bị bếp chay")
                        .icon("zap")
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
        if (lowerName.contains("điện máy xanh") || lowerName.contains("dien may xanh") || types.contains("electronics_store")) {
            return PlaceCategory.DIEN_MAY_XANH;
        }
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
            case DIEN_MAY_XANH -> "Điện Máy Xanh";
            case ALL -> "quán chay|thực phẩm chay|siêu thị chay|Điện Máy Xanh";
        };
        if (customKeyword != null && !customKeyword.isBlank()) {
            return base + "|" + customKeyword.trim();
        }
        return base;
    }

    // ── DB + Smart Fallback Generator ─────────────────────────────────────────

    private List<PlaceResponse> findDbPlacesWithinRadius(double userLat,
                                                         double userLng,
                                                         int radiusMeters,
                                                         PlaceCategory requestedCategory,
                                                         String keyword) {
        double maxRadiusKm = radiusMeters / 1000.0;
        return mapPlaceRepository.findAll().stream()
                .filter(p -> requestedCategory == PlaceCategory.ALL || p.getCategory() == requestedCategory)
                .filter(p -> matchesKeyword(p.getName(), p.getAddress(), keyword))
                .map(p -> toPlaceResponse(p, userLat, userLng))
                .filter(p -> p.getDistanceKm() <= maxRadiusKm)
                .toList();
    }

    private PlaceResponse toPlaceResponse(MapPlace p, double userLat, double userLng) {
        double distKm = GeoUtils.calculateHaversineDistanceKm(userLat, userLng, p.getLatitude(), p.getLongitude());
        return PlaceResponse.builder()
                .placeId(p.getPlaceId())
                .name(p.getName())
                .category(p.getCategory())
                .address(p.getAddress())
                .latitude(p.getLatitude())
                .longitude(p.getLongitude())
                .distanceKm(distKm)
                .distanceText(GeoUtils.formatDistanceText(distKm))
                .rating(p.getRating())
                .userRatingsTotal(p.getUserRatingsTotal())
                .isOpenNow(p.getIsOpenNow())
                .photoUrl(p.getPhotoUrl())
                .googleMapsUrl(GeoUtils.buildGoogleMapsDirectionsUrl(p.getLatitude(), p.getLongitude(), p.getPlaceId()))
                .phoneNumber(p.getPhoneNumber())
                .types(List.of(p.getCategory().name().toLowerCase(), p.isCreatedByAdmin() ? "admin_added" : "verified"))
                .isVerified(p.isVerified())
                .build();
    }

    private boolean matchesKeyword(String name, String address, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        String kw = keyword.toLowerCase().trim();
        String n = name != null ? name.toLowerCase() : "";
        String a = address != null ? address.toLowerCase() : "";
        return n.contains(kw) || a.contains(kw);
    }

    private void mergeUniquePlaces(List<PlaceResponse> target, List<PlaceResponse> additions) {
        Set<String> existingNames = new HashSet<>();
        for (PlaceResponse p : target) {
            if (p.getName() != null) existingNames.add(p.getName().toLowerCase().trim());
        }
        for (PlaceResponse add : additions) {
            String key = add.getName() != null ? add.getName().toLowerCase().trim() : "";
            if (!existingNames.contains(key)) {
                target.add(add);
                existingNames.add(key);
            }
        }
    }

    private List<PlaceResponse> generateFallbackPlaces(double userLat,
                                                       double userLng,
                                                       int radiusMeters,
                                                       PlaceCategory requestedCategory,
                                                       String keyword) {
        // 1. Lấy các địa điểm thực tế đã lưu trong DB nằm trong bán kính
        List<PlaceResponse> combined = new ArrayList<>(
                findDbPlacesWithinRadius(userLat, userLng, radiusMeters, requestedCategory, keyword)
        );

        // 2. Bổ sung các địa điểm quanh tọa độ người dùng (đảm bảo mọi vị trí trên bản đồ đều có gợi ý gần)
        List<PlaceResponse> relativeList = new ArrayList<>();
        addMock(relativeList, userLat, userLng, userLat + 0.0035, userLng + 0.0028, "Cơm Chay Mandala",
                PlaceCategory.RESTAURANT, "45 Thạch Thị Thanh, Tân Định, Quận 1", 4.8, 234, true,
                "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500", "028 3820 5432");

        addMock(relativeList, userLat, userLng, userLat - 0.0042, userLng + 0.0031, "Nhà Hàng Chay Thiện Duyên",
                PlaceCategory.RESTAURANT, "86 Nguyễn Đình Chiểu, Đa Kao, Quận 1", 4.7, 182, true,
                "https://images.unsplash.com/photo-1540420773420-3366772f4999?w=500", "028 3910 1122");

        addMock(relativeList, userLat, userLng, userLat + 0.0061, userLng - 0.0045, "Quán Chay Thanh Lương",
                PlaceCategory.RESTAURANT, "545 Cách Mạng Tháng 8, Phường 15, Quận 10", 4.6, 95, true,
                "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=500", "090 312 3456");

        addMock(relativeList, userLat, userLng, userLat - 0.0075, userLng - 0.0038, "Buffet Chay Mãn Tự",
                PlaceCategory.RESTAURANT, "201 Nguyễn Thị Minh Khai, Nguyễn Cư Trinh, Quận 1", 4.9, 512, true,
                "https://images.unsplash.com/photo-1547592180-85f173990554?w=500", "093 888 9999");

        addMock(relativeList, userLat, userLng, userLat + 0.0022, userLng - 0.0021, "Siêu Thị Thực Phẩm Chay Đại An",
                PlaceCategory.SUPERMARKET, "112 Bùi Thị Xuân, Bến Thành, Quận 1", 4.9, 140, true,
                "https://images.unsplash.com/photo-1578916171728-46686eac8d58?w=500", "028 3925 6789");

        addMock(relativeList, userLat, userLng, userLat - 0.0051, userLng - 0.0055, "Cửa Hàng Thực Phẩm Chay Tuệ Đức",
                PlaceCategory.GROCERY_STORE, "240 Trần Hưng Đạo, Phường 11, Quận 5", 4.6, 88, true,
                "https://images.unsplash.com/photo-1506484381205-f7945653044d?w=500", "091 234 5678");

        addMock(relativeList, userLat, userLng, userLat + 0.0085, userLng + 0.0062, "Bách Hóa Chay Thuần Chay Store",
                PlaceCategory.SUPERMARKET, "18A Điện Biên Phủ, Phường 25, Bình Thạnh", 4.8, 175, true,
                "https://images.unsplash.com/photo-1542838132-92c53300491e?w=500", "028 6258 7890");

        addMock(relativeList, userLat, userLng, userLat - 0.0019, userLng + 0.0054, "Tiệm Đậu Phụ Sạch & Nấm Tươi Hoàng Gia",
                PlaceCategory.GROCERY_STORE, "72 Võ Văn Tần, Phường 6, Quận 3", 4.7, 63, true,
                "https://images.unsplash.com/photo-1589927986089-35812388d1f4?w=500", "098 765 4321");

        addMock(relativeList, userLat, userLng, userLat - 0.0031, userLng - 0.0042, "Điện Máy Xanh Nguyễn Thị Minh Khai",
                PlaceCategory.DIEN_MAY_XANH, "189A Nguyễn Thị Minh Khai, Phường Phạm Ngũ Lão, Quận 1", 4.8, 420, true,
                "https://images.unsplash.com/photo-1556911220-bff31c812dba?w=500", "1900 232 461");

        addMock(relativeList, userLat, userLng, userLat + 0.0045, userLng - 0.0065, "Điện Máy Xanh Cách Mạng Tháng 8",
                PlaceCategory.DIEN_MAY_XANH, "328 Cách Mạng Tháng 8, Phường 10, Quận 3", 4.7, 315, true,
                "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?w=500", "1900 232 461");

        double maxRadiusKm = radiusMeters / 1000.0;
        List<PlaceResponse> filteredRelative = relativeList.stream()
                .filter(p -> p.getDistanceKm() <= maxRadiusKm)
                .filter(p -> requestedCategory == PlaceCategory.ALL || p.getCategory() == requestedCategory)
                .filter(p -> matchesKeyword(p.getName(), p.getAddress(), keyword))
                .toList();

        mergeUniquePlaces(combined, filteredRelative);
        combined.sort(Comparator.comparingDouble(PlaceResponse::getDistanceKm));
        return combined;
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

    private void evictPlacesCache() {
        if (redisTemplate == null) return;
        try {
            Set<String> keys = redisTemplate.keys("places:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.debug("[REDIS-CACHE] Bỏ qua lỗi xóa cache: {}", e.getMessage());
        }
    }
}
