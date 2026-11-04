package com.vegeai.backend.modules.place.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.place.dto.CategoryMetadataDto;
import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.dto.PlaceResponse;
import com.vegeai.backend.modules.place.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenStreetMapPlaceProvider implements PlaceProvider {

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Duration STALE_CACHE_TTL = Duration.ofDays(7);
    private static final Duration GEOCODE_CACHE_TTL = Duration.ofDays(1);
    private static final String STALE_PREFIX = "places:osm:nearby:stale:";
    private static final long FAST_FAILURE_RETRY_MS = 5_000;
    private static final String OSM_ATTRIBUTION = "© OpenStreetMap contributors";
    private static final RestClient REST_CLIENT = createRestClient();
    private static long lastNominatimRequestAt;

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        // Overpass regularly needs 25-35s for a city-sized query; 20s made every call fail.
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader("User-Agent", "VEGEAI-Backend/1.0 (OpenStreetMap place search)")
            .defaultHeader("Accept-Language", "vi,en")
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.openstreetmap.overpass-url:https://overpass-api.de/api/interpreter}")
    private String overpassUrl;

    @Value("${app.openstreetmap.nominatim-url:https://nominatim.openstreetmap.org}")
    private String nominatimUrl;

    @Override
    public List<PlaceResponse> findNearbyVegetarianPlaces(
            double latitude,
            double longitude,
            int radiusMeters,
            PlaceCategory category,
            String keyword) {
        int radius = boundRadius(radiusMeters);
        PlaceCategory requestedCategory = category == null ? PlaceCategory.ALL : category;
        String cacheKey = String.format(Locale.ROOT, "places:osm:nearby:%d:%d:%d:%s:%s",
                Math.round(latitude * 10000), Math.round(longitude * 10000), radius,
                requestedCategory, normalize(keyword));
        List<PlaceResponse> cached = getFromCache(cacheKey);
        if (cached != null) return cached;

        List<PlaceResponse> results;
        try {
            results = queryNearby(latitude, longitude, radius).stream()
                    .filter(place -> requestedCategory == PlaceCategory.ALL || requestedCategory == place.getCategory())
                    .filter(place -> keyword == null || keyword.isBlank() || matchesKeyword(place, keyword))
                    .sorted(Comparator.comparing(PlaceResponse::getDistanceKm))
                    .toList();
        } catch (AppException exception) {
            List<PlaceResponse> stale = getFromCache(STALE_PREFIX + cacheKey);
            if (stale != null && !stale.isEmpty()) {
                log.warn("[PLACES-OSM] Serving {} cached places after provider failure.", stale.size());
                return stale;
            }
            try {
                List<PlaceResponse> nominatimFallback = queryNominatimNearby(
                        latitude, longitude, radius, requestedCategory, keyword);
                if (!nominatimFallback.isEmpty()) {
                    log.warn("[PLACES-OSM] Overpass unavailable, served {} places from Nominatim.", nominatimFallback.size());
                    saveToCache(cacheKey, nominatimFallback, Duration.ofMinutes(30));
                    saveToCache(STALE_PREFIX + cacheKey, nominatimFallback, STALE_CACHE_TTL);
                    return nominatimFallback;
                }
            } catch (Exception nominatimError) {
                log.warn("[PLACES-OSM] Nominatim fallback failed: {}", nominatimError.getMessage());
            }
            throw exception;
        }
        saveToCache(cacheKey, results);
        saveToCache(STALE_PREFIX + cacheKey, results, STALE_CACHE_TTL);
        return results;
    }

    @Override
    public List<PlaceResponse> searchVegetarianPlaces(
            String query,
            Double latitude,
            Double longitude,
            int radiusMeters,
            PlaceCategory category) {
        String searchText = query == null ? "" : query.trim();
        if (searchText.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Search query cannot be empty.");
        }
        int radius = boundRadius(radiusMeters);
        PlaceCategory requestedCategory = category == null ? PlaceCategory.ALL : category;

        double centerLat;
        double centerLng;
        if (latitude != null && longitude != null) {
            centerLat = latitude;
            centerLng = longitude;
        } else {
            double[] coordinates = geocode(searchText);
            if (coordinates == null) return List.of();
            centerLat = coordinates[0];
            centerLng = coordinates[1];
        }

        String cacheKey = String.format(Locale.ROOT, "places:osm:search:%s:%d:%d:%d:%s",
                normalize(searchText), Math.round(centerLat * 10000), Math.round(centerLng * 10000),
                radius, requestedCategory);
        List<PlaceResponse> cached = getFromCache(cacheKey);
        if (cached != null) return cached;

        List<PlaceResponse> results = queryNearby(centerLat, centerLng, radius).stream()
                .filter(place -> requestedCategory == PlaceCategory.ALL || requestedCategory == place.getCategory())
                .sorted(Comparator.comparing(PlaceResponse::getDistanceKm))
                .toList();
        saveToCache(cacheKey, results);
        return results;
    }

    @Override
    public List<CategoryMetadataDto> getCategoryMetadata() {
        return List.of(
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.ALL)
                        .displayName("Tất cả địa điểm có thẻ chay trên OpenStreetMap")
                        .description("Các địa điểm do cộng đồng OpenStreetMap gắn thẻ chay; dữ liệu có thể chưa đầy đủ.")
                        .icon("map-pin")
                        .build(),
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.RESTAURANT)
                        .displayName("Quán ăn")
                        .description("Nhà hàng, quán cà phê hoặc thức ăn nhanh có thẻ chay trên OpenStreetMap.")
                        .icon("utensils")
                        .build(),
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.SUPERMARKET)
                        .displayName("Siêu thị")
                        .description("Siêu thị có thẻ chay trên OpenStreetMap.")
                        .icon("shopping-cart")
                        .build(),
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.GROCERY_STORE)
                        .displayName("Cửa hàng thực phẩm")
                        .description("Cửa hàng thực phẩm có thẻ chay trên OpenStreetMap.")
                        .icon("store")
                        .build()
        );
    }

    @Override
    public String reverseGeocode(double latitude, double longitude) {
        String cacheKey = String.format(Locale.ROOT, "places:osm:reverse:%d:%d",
                Math.round(latitude * 10000), Math.round(longitude * 10000));
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) return cached;
        } catch (Exception exception) {
            log.debug("[PLACES-OSM] Reverse cache unavailable: {}", exception.getMessage());
        }
        synchronized (OpenStreetMapPlaceProvider.class) {
            try {
                long waitMillis = 1000 - (System.currentTimeMillis() - lastNominatimRequestAt);
                if (lastNominatimRequestAt > 0 && waitMillis > 0) Thread.sleep(waitMillis);
                lastNominatimRequestAt = System.currentTimeMillis();
                URI uri = UriComponentsBuilder.fromUriString(nominatimUrl)
                        .path("/reverse")
                        .queryParam("format", "jsonv2")
                        .queryParam("lat", latitude)
                        .queryParam("lon", longitude)
                        .queryParam("zoom", 18)
                        .build().encode().toUri();
                JsonNode result = objectMapper.readTree(REST_CLIENT.get().uri(uri).retrieve().body(byte[].class));
                String address = result.path("display_name").asText();
                if (address.isBlank()) address = String.format(Locale.ROOT, "Vị trí GPS (%.4f, %.4f)", latitude, longitude);
                try { redisTemplate.opsForValue().set(cacheKey, address, GEOCODE_CACHE_TTL); } catch (Exception ignored) {}
                return address;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
            } catch (Exception exception) {
                log.warn("[PLACES-OSM] Reverse geocoding failed: {}", exception.getMessage());
                throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
            }
        }
    }

    private List<PlaceResponse> queryNearby(double latitude, double longitude, int radiusMeters) {
        long startedAt = System.currentTimeMillis();
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String query = buildOverpassQuery(latitude, longitude, radiusMeters);
                String form = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
                byte[] response = REST_CLIENT.post()
                        .uri(overpassUrl)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .exchange((request, clientResponse) -> {
                            if (clientResponse.getStatusCode().isError()) {
                                throw new IllegalStateException("Overpass returned HTTP " + clientResponse.getStatusCode().value());
                            }
                            return clientResponse.getBody().readAllBytes();
                        });
                if (response == null) throw new IllegalStateException("Overpass returned an empty response.");
                return parseOverpassResponse(new String(response, StandardCharsets.UTF_8), latitude, longitude, objectMapper);
            } catch (AppException exception) {
                throw exception;
            } catch (Exception exception) {
                long elapsed = System.currentTimeMillis() - startedAt;
                log.warn("[PLACES-OSM] Overpass attempt {}/2 failed after {} ms: {}", attempt, elapsed, exception.getMessage());
                // A read timeout already burned the full budget; retrying would only double the wait.
                if (attempt == 1 && elapsed < FAST_FAILURE_RETRY_MS) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
            }
        }
        throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
    }

    private double[] geocode(String query) {
        String normalizedQuery = normalize(query);
        String cacheKey = "places:osm:geocode:" + normalizedQuery;
        double[] cached = getGeocodeFromCache(cacheKey);
        if (cached != null) return cached.length == 0 ? null : cached;

        synchronized (OpenStreetMapPlaceProvider.class) {
            cached = getGeocodeFromCache(cacheKey);
            if (cached != null) return cached.length == 0 ? null : cached;
            try {
                long waitMillis = 1000 - (System.currentTimeMillis() - lastNominatimRequestAt);
                if (lastNominatimRequestAt > 0 && waitMillis > 0) Thread.sleep(waitMillis);
                lastNominatimRequestAt = System.currentTimeMillis();
                double[] coordinates = requestGeocode(query);
                saveGeocodeToCache(cacheKey, coordinates);
                return coordinates;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
            }
        }
    }

    private double[] requestGeocode(String query) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(nominatimUrl)
                    .path("/search")
                    .queryParam("q", query)
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", 1)
                    .build()
                    .encode()
                    .toUri();
            byte[] response = REST_CLIENT.get().uri(uri).retrieve().body(byte[].class);
            if (response == null) return null;
            JsonNode results = objectMapper.readTree(response);
            if (!results.isArray() || results.isEmpty()) return null;
            JsonNode first = results.get(0);
            return new double[]{first.path("lat").asDouble(), first.path("lon").asDouble()};
        } catch (Exception exception) {
            log.warn("[PLACES-OSM] Nominatim geocoding failed: {}", exception.getMessage());
            throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
        }
    }

    private double[] getGeocodeFromCache(String key) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                if (cached.isEmpty()) return new double[0];
                String[] coordinates = cached.split(",", -1);
                if (coordinates.length == 2) {
                    return new double[]{Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1])};
                }
            }
        } catch (Exception exception) {
            log.debug("[PLACES-OSM-CACHE] Geocode cache read skipped: {}", exception.getMessage());
        }
        return null;
    }

    private void saveGeocodeToCache(String key, double[] coordinates) {
        try {
            String value = coordinates == null ? "" : coordinates[0] + "," + coordinates[1];
            redisTemplate.opsForValue().set(key, value, GEOCODE_CACHE_TTL);
        } catch (Exception exception) {
            log.debug("[PLACES-OSM-CACHE] Geocode cache write skipped: {}", exception.getMessage());
        }
    }

    static List<PlaceResponse> parseOverpassResponse(String response, double latitude, double longitude,
                                                     ObjectMapper objectMapper) throws Exception {
        JsonNode elements = objectMapper.readTree(response).path("elements");
        if (!elements.isArray()) {
            throw new IllegalArgumentException("Overpass response is missing its elements array.");
        }
        List<PlaceResponse> places = new ArrayList<>();
        for (JsonNode element : elements) {
            JsonNode tagsNode = element.path("tags");
            if (!tagsNode.isObject() || tagsNode.path("name").asText("").isBlank()) continue;

            double placeLat = element.path("lat").asDouble(element.path("center").path("lat").asDouble(Double.NaN));
            double placeLng = element.path("lon").asDouble(element.path("center").path("lon").asDouble(Double.NaN));
            if (!Double.isFinite(placeLat) || !Double.isFinite(placeLng)
                    || placeLat < -90 || placeLat > 90 || placeLng < -180 || placeLng > 180) continue;

            Map<String, String> tags = new HashMap<>();
            tagsNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().isValueNode()) tags.put(entry.getKey(), entry.getValue().asText());
            });
            if (!hasVegetarianTag(tags)) continue;

            String osmType = element.path("type").asText();
            String osmId = element.path("id").asText();
            String placeId = osmType + "/" + osmId;
            double distanceKm = GeoUtils.calculateHaversineDistanceKm(latitude, longitude, placeLat, placeLng);
            boolean explicitlyVegetarianOnly = hasExclusiveVegetarianTag(tags);

            places.add(PlaceResponse.builder()
                    .placeId(placeId)
                    .name(tags.get("name"))
                    .category(classifyCategory(tags))
                    .address(buildAddress(tags))
                    .latitude(placeLat)
                    .longitude(placeLng)
                    .distanceKm(distanceKm)
                    .distanceText(GeoUtils.formatDistanceText(distanceKm))
                    .rating(null)
                    .userRatingsTotal(null)
                    .isOpenNow(null)
                    .photoUrl(safeImageUrl(tags.get("image")))
                    .openStreetMapUrl(buildOpenStreetMapUrl(osmType, osmId, placeLat, placeLng))
                    .phoneNumber(tags.get("phone"))
                    .types(tags.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).sorted().toList())
                    .isVerified(explicitlyVegetarianOnly)
                    .verificationSource(explicitlyVegetarianOnly ? "OPENSTREETMAP_TAGS" : null)
                    .dataSource("OPENSTREETMAP")
                    .attribution(OSM_ATTRIBUTION)
                    .build());
        }
        places.sort(Comparator.comparing(PlaceResponse::getDistanceKm));
        return places;
    }

    /**
     * Nominatim fallback used when the Overpass API is slow or unreachable. It is fast (1-3s) and
     * respects the public usage policy by waiting at least one second between searches.
     */
    private List<PlaceResponse> queryNominatimNearby(
            double latitude, double longitude, int radiusMeters, PlaceCategory category, String keyword) {
        List<String> terms = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            terms.add(keyword.trim());
        } else if (category == PlaceCategory.RESTAURANT) {
            terms.add("quán chay");
        } else if (category == PlaceCategory.SUPERMARKET || category == PlaceCategory.GROCERY_STORE) {
            terms.add("cửa hàng thực phẩm");
        } else {
            terms.add("nhà hàng chay");
            terms.add("quán cơm chay");
            terms.add("cửa hàng thực phẩm");
        }

        Map<String, PlaceResponse> unique = new LinkedHashMap<>();
        for (String term : terms) {
            try {
                for (PlaceResponse place : searchNominatim(term, latitude, longitude, radiusMeters, category)) {
                    unique.putIfAbsent(place.getPlaceId(), place);
                }
            } catch (Exception exception) {
                log.warn("[PLACES-NOMINATIM] Search '{}' failed: {}", term, exception.getMessage());
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(PlaceResponse::getDistanceKm))
                .limit(30)
                .toList();
    }

    private List<PlaceResponse> searchNominatim(
            String term, double latitude, double longitude, int radiusMeters, PlaceCategory requestedCategory) throws Exception {
        double deltaLat = radiusMeters / 111_320.0;
        double deltaLon = radiusMeters / (111_320.0 * Math.max(0.2, Math.cos(Math.toRadians(latitude))));
        String viewbox = String.format(Locale.ROOT, "%f,%f,%f,%f",
                longitude - deltaLon, latitude + deltaLat, longitude + deltaLon, latitude - deltaLat);
        waitForNominatim();
        URI uri = UriComponentsBuilder.fromUriString(nominatimUrl)
                .path("/search")
                .queryParam("format", "jsonv2")
                .queryParam("q", term)
                .queryParam("viewbox", viewbox)
                .queryParam("bounded", "1")
                .queryParam("limit", "15")
                .queryParam("extratags", "1")
                .queryParam("addressdetails", "1")
                .queryParam("accept-language", "vi")
                .build().encode().toUri();
        JsonNode payload = null;
        Exception lastError = null;
        for (int attempt = 1; attempt <= 2 && payload == null; attempt++) {
            try {
                payload = objectMapper.readTree(REST_CLIENT.get().uri(uri).retrieve().body(byte[].class));
            } catch (Exception exception) {
                lastError = exception;
                log.warn("[PLACES-NOMINATIM] Search '{}' attempt {}/2 failed: {}", term, attempt, exception.getMessage());
                if (attempt == 1) {
                    try {
                        Thread.sleep(700);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (payload == null) throw lastError == null ? new IllegalStateException("Nominatim returned no payload.") : lastError;
        if (!payload.isArray()) return List.of();
        List<PlaceResponse> places = new ArrayList<>();
        for (JsonNode item : payload) {
            PlaceResponse place = toNominatimPlace(item, latitude, longitude);
            if (place == null) continue;
            if (requestedCategory != PlaceCategory.ALL && place.getCategory() != requestedCategory) continue;
            places.add(place);
        }
        return places;
    }

    private void waitForNominatim() {
        synchronized (OpenStreetMapPlaceProvider.class) {
            long waitMillis = 1100 - (System.currentTimeMillis() - lastNominatimRequestAt);
            if (lastNominatimRequestAt > 0 && waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            lastNominatimRequestAt = System.currentTimeMillis();
        }
    }

    private PlaceResponse toNominatimPlace(JsonNode item, double userLat, double userLon) {
        String placeClass = item.path("class").asText(item.path("category").asText(""));
        PlaceCategory category = classifyNominatim(placeClass, item.path("type").asText(""));
        if (category == null) return null;
        double latitude = item.path("lat").asDouble();
        double longitude = item.path("lon").asDouble();
        if (latitude == 0 || longitude == 0) return null;
        String displayName = item.path("display_name").asText("");
        String name = item.path("name").asText("");
        if (name.isBlank() && !displayName.isBlank()) name = displayName.split(",")[0].trim();
        if (name.isBlank()) return null;

        String osmType = item.path("osm_type").asText("node");
        String osmId = item.path("osm_id").asText("");
        JsonNode extraTags = item.path("extratags");
        Map<String, String> tags = new LinkedHashMap<>();
        extraTags.fields().forEachRemaining(entry -> tags.put(entry.getKey(), entry.getValue().asText()));
        List<String> types = tags.entrySet().stream()
                .limit(8)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        double distanceKm = Math.round(GeoUtils.calculateExactHaversineDistanceKm(userLat, userLon, latitude, longitude) * 100.0) / 100.0;

        return PlaceResponse.builder()
                .placeId(osmType + "/" + osmId)
                .name(name)
                .category(category)
                .address(displayName)
                .latitude(latitude)
                .longitude(longitude)
                .distanceKm(distanceKm)
                .distanceText(GeoUtils.formatDistanceText(distanceKm))
                .phoneNumber(tags.get("phone"))
                .types(types)
                .isVerified(hasVegetarianTag(tags))
                .verificationSource(hasVegetarianTag(tags) ? "OPENSTREETMAP_TAGS" : null)
                .dataSource("OPENSTREETMAP_NOMINATIM")
                .openStreetMapUrl("https://www.openstreetmap.org/" + osmType + "/" + osmId)
                .attribution(OSM_ATTRIBUTION)
                .build();
    }

    private static PlaceCategory classifyNominatim(String placeClass, String type) {
        String normalizedClass = normalize(placeClass);
        String normalizedType = normalize(type);
        if (Set.of("restaurant", "cafe", "fast_food", "food_court", "pub", "bar").contains(normalizedType)) {
            return PlaceCategory.RESTAURANT;
        }
        if ("supermarket".equals(normalizedType)) {
            return PlaceCategory.SUPERMARKET;
        }
        if (Set.of("convenience", "greengrocer", "health_food", "organic", "farm", "deli", "general").contains(normalizedType)) {
            return PlaceCategory.GROCERY_STORE;
        }
        if ("amenity".equals(normalizedClass) && normalizedType.isBlank()) {
            return PlaceCategory.RESTAURANT;
        }
        return null;
    }

    private String buildOverpassQuery(double latitude, double longitude, int radiusMeters) {
        String around = "(around:" + radiusMeters + "," + latitude + "," + longitude + ")";
        return "[out:json][timeout:20];("
                + "nwr[\"cuisine\"~\"vegetarian|vegan\",i]" + around + ";"
                + "nwr[\"diet:vegetarian\"~\"yes|only\",i]" + around + ";"
                + "nwr[\"diet:vegan\"~\"yes|only\",i]" + around + ";"
                + ");out center tags;";
    }

    private static boolean hasVegetarianTag(Map<String, String> tags) {
        String vegetarian = normalize(tags.get("diet:vegetarian"));
        String vegan = normalize(tags.get("diet:vegan"));
        String cuisine = normalize(tags.get("cuisine"));
        return (vegetarian.equals("yes") || vegetarian.equals("only"))
                || (vegan.equals("yes") || vegan.equals("only"))
                || Arrays.stream(cuisine.split("[;,]")).map(String::trim)
                .anyMatch(value -> value.equals("vegetarian") || value.equals("vegan"));
    }

    private static boolean hasExclusiveVegetarianTag(Map<String, String> tags) {
        String vegetarian = normalize(tags.get("diet:vegetarian"));
        String vegan = normalize(tags.get("diet:vegan"));
        String cuisine = normalize(tags.get("cuisine"));
        String[] cuisines = cuisine.split("[;,]");
        return vegetarian.equals("only") || vegan.equals("only")
                || cuisines.length > 0 && Arrays.stream(cuisines).map(String::trim)
                .allMatch(value -> value.equals("vegetarian") || value.equals("vegan"));
    }

    private static PlaceCategory classifyCategory(Map<String, String> tags) {
        String shop = normalize(tags.get("shop"));
        if (shop.equals("supermarket")) return PlaceCategory.SUPERMARKET;
        if (Set.of("convenience", "greengrocer", "health_food", "grocery", "deli").contains(shop)) {
            return PlaceCategory.GROCERY_STORE;
        }
        return PlaceCategory.RESTAURANT;
    }

    private static String buildAddress(Map<String, String> tags) {
        String fullAddress = tags.get("addr:full");
        if (fullAddress != null && !fullAddress.isBlank()) return fullAddress;
        List<String> parts = new ArrayList<>();
        String houseNumber = tags.get("addr:housenumber");
        String street = tags.get("addr:street");
        if (houseNumber != null) parts.add(houseNumber);
        if (street != null) parts.add(street);
        for (String key : List.of("addr:suburb", "addr:city", "addr:state")) {
            String value = tags.get(key);
            if (value != null && !value.isBlank()) parts.add(value);
        }
        return String.join(", ", parts);
    }

    private static String safeImageUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return null;
        try {
            URI uri = URI.create(rawUrl);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null ? rawUrl : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String buildOpenStreetMapUrl(String type, String id, double latitude, double longitude) {
        if (Set.of("node", "way", "relation").contains(type)) {
            return "https://www.openstreetmap.org/" + type + "/" + id;
        }
        return String.format(Locale.US, "https://www.openstreetmap.org/?mlat=%f&mlon=%f#map=18/%f/%f",
                latitude, longitude, latitude, longitude);
    }

    private boolean matchesKeyword(PlaceResponse place, String keyword) {
        String normalized = normalize(keyword);
        return normalize(place.getName()).contains(normalized)
                || normalize(place.getAddress()).contains(normalized)
                || place.getTypes().stream().anyMatch(type -> normalize(type).contains(normalized));
    }

    private int boundRadius(int radiusMeters) {
        return Math.max(500, Math.min(radiusMeters, 50000));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private List<PlaceResponse> getFromCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readerForListOf(PlaceResponse.class).readValue(json);
            }
        } catch (Exception exception) {
            log.debug("[PLACES-OSM-CACHE] Cache read skipped: {}", exception.getMessage());
        }
        return null;
    }

    private void saveToCache(String key, List<PlaceResponse> data) {
        saveToCache(key, data, CACHE_TTL);
    }

    private void saveToCache(String key, List<PlaceResponse> data, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), ttl);
        } catch (Exception exception) {
            log.debug("[PLACES-OSM-CACHE] Cache write skipped: {}", exception.getMessage());
        }
    }
}
