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
import java.text.Normalizer;
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
        requestFactory.setConnectTimeout(Duration.ofSeconds(4));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
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
    private String overpassUrl = "https://overpass-api.de/api/interpreter";

    @Value("${app.osm.overpass-urls:https://overpass-api.de/api/interpreter,https://overpass.private.coffee/api/interpreter,https://lz4.overpass-api.de/api/interpreter,https://z.overpass-api.de/api/interpreter}")
    private String overpassUrls = "https://overpass-api.de/api/interpreter,https://overpass.private.coffee/api/interpreter,https://lz4.overpass-api.de/api/interpreter,https://z.overpass-api.de/api/interpreter";

    @Value("${app.openstreetmap.nominatim-url:https://nominatim.openstreetmap.org}")
    private String nominatimUrl = "https://nominatim.openstreetmap.org";

    @Value("${app.osm.photon-url:https://photon.komoot.io/api/}")
    private String photonUrl = "https://photon.komoot.io/api/";

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

        return queryPlacesResilient(
                latitude, longitude, radius, requestedCategory, keyword, keyword,
                cacheKey, Comparator.comparing(PlaceResponse::getDistanceKm));
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

        String relevanceQuery = latitude != null && longitude != null ? searchText : null;
        return queryPlacesResilient(
                centerLat, centerLng, radius, requestedCategory, null, relevanceQuery,
                cacheKey, foodRelevanceComparator(relevanceQuery));
    }

    private List<PlaceResponse> queryPlacesResilient(
            double latitude,
            double longitude,
            int radius,
            PlaceCategory requestedCategory,
            String filterKeyword,
            String fallbackSearchTerm,
            String cacheKey,
            Comparator<PlaceResponse> comparator) {
        List<PlaceResponse> results;
        try {
            results = queryNearby(latitude, longitude, radius).stream()
                    .filter(place -> requestedCategory == PlaceCategory.ALL || requestedCategory == place.getCategory())
                    .filter(place -> filterKeyword == null || filterKeyword.isBlank() || matchesKeyword(place, filterKeyword))
                    .sorted(comparator)
                    .toList();
        } catch (AppException exception) {
            List<PlaceResponse> stale = getFromCache(STALE_PREFIX + cacheKey);
            if (stale != null && !stale.isEmpty()) {
                log.warn("[PLACES-OSM] Serving {} cached places after provider failure.", stale.size());
                return stale;
            }
            try {
                List<PlaceResponse> fallback = queryNominatimNearby(
                        latitude, longitude, radius, requestedCategory, fallbackSearchTerm)
                        .stream()
                        .sorted(comparator)
                        .toList();
                if (!fallback.isEmpty()) {
                    log.warn("[PLACES-OSM] Overpass unavailable, served {} places from geocoder fallback.", fallback.size());
                    saveToCache(cacheKey, fallback, Duration.ofMinutes(30));
                    saveToCache(STALE_PREFIX + cacheKey, fallback, STALE_CACHE_TTL);
                    return fallback;
                }
            } catch (Exception fallbackError) {
                log.warn("[PLACES-OSM] Geocoder fallback failed: {}", fallbackError.getMessage());
            }
            throw exception;
        }
        saveToCache(cacheKey, results);
        saveToCache(STALE_PREFIX + cacheKey, results, STALE_CACHE_TTL);
        return results;
    }

    @Override
    public List<PlaceResponse> suggestShopsForFood(
            String food,
            Double latitude,
            Double longitude,
            int radiusMeters) {
        String searchText = food == null ? "" : food.trim();
        if (searchText.isEmpty() || (latitude == null) != (longitude == null)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Vui lòng nhập món ăn cần tìm và cung cấp đủ tọa độ lat/lng nếu tìm theo vị trí.");
        }
        if (latitude == null) {
            double[] coordinates = geocode(searchText);
            if (coordinates == null) return List.of();
            latitude = coordinates[0];
            longitude = coordinates[1];
        }
        int radius = boundRadius(radiusMeters);
        List<PlaceResponse> nearbyPlaces = findNearbyVegetarianPlaces(latitude, longitude, radius, PlaceCategory.ALL, null);
        return nearbyPlaces.stream()
                .sorted(foodRelevanceComparator(searchText))
                .toList();
    }

    @Override
    public List<CategoryMetadataDto> getCategoryMetadata() {
        return List.of(
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.ALL)
                        .displayName("Tất cả địa điểm ăn chay & nguyên liệu")
                        .description("Quán ăn chay hoặc có món chay, siêu thị, cửa hàng tiện lợi và cửa hàng thực phẩm từ OpenStreetMap.")
                        .icon("map-pin")
                        .build(),
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.RESTAURANT)
                        .displayName("Quán ăn / Nhà hàng")
                        .description("Quán chuyên chay hoặc nhà hàng, quán cà phê có phục vụ món chay trên OpenStreetMap.")
                        .icon("utensils")
                        .build(),
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.SUPERMARKET)
                        .displayName("Siêu thị")
                        .description("Siêu thị có bán nguyên liệu, rau củ và thực phẩm chay trên OpenStreetMap.")
                        .icon("shopping-cart")
                        .build(),
                CategoryMetadataDto.builder()
                        .category(PlaceCategory.GROCERY_STORE)
                        .displayName("Cửa hàng tiện lợi & Thực phẩm")
                        .description("Cửa hàng tiện lợi, cửa hàng thực phẩm, rau củ và nguyên liệu chế biến món chay trên OpenStreetMap.")
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
        String osmAddress = null;
        synchronized (OpenStreetMapPlaceProvider.class) {
            try {
                waitForNominatimRateLimit();
                URI uri = UriComponentsBuilder.fromUriString(nominatimUrl)
                        .path("/reverse")
                        .queryParam("format", "jsonv2")
                        .queryParam("lat", latitude)
                        .queryParam("lon", longitude)
                        .queryParam("zoom", 18)
                        .build().encode().toUri();
                JsonNode result = objectMapper.readTree(REST_CLIENT.get().uri(uri).retrieve().body(byte[].class));
                osmAddress = result.path("display_name").asText();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
            } catch (Exception exception) {
                log.warn("[PLACES-OSM] Nominatim reverse geocoding failed, trying Photon fallback: {}", exception.getMessage());
            }
        }
        if (osmAddress == null || osmAddress.isBlank()) {
            osmAddress = reverseGeocodeWithPhoton(latitude, longitude);
        }
        if (osmAddress == null) {
            throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
        }
        if (osmAddress.isBlank()) {
            osmAddress = String.format(Locale.ROOT, "Vị trí GPS (%.4f, %.4f)", latitude, longitude);
        }
        try { redisTemplate.opsForValue().set(cacheKey, osmAddress, GEOCODE_CACHE_TTL); } catch (Exception ignored) {}
        return osmAddress;
    }

    private List<PlaceResponse> queryNearby(double latitude, double longitude, int radiusMeters) {
        String query = buildOverpassQuery(latitude, longitude, radiusMeters);
        String form = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        List<String> endpoints = resolveOverpassEndpoints();
        boolean singleLoopback = endpoints.size() == 1 && isLoopbackEndpoint(endpoints.get(0));
        int maxAttempts = singleLoopback ? 2 : endpoints.size();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String endpoint = singleLoopback ? endpoints.get(0) : endpoints.get(attempt);
            boolean loopback = isLoopbackEndpoint(endpoint);
            String downKey = "places:osm:overpass-down:" + Integer.toHexString(endpoint.hashCode());
            if (!loopback && isMirrorTemporarilyDown(downKey)) {
                continue;
            }
            long startedAt = System.currentTimeMillis();
            try {
                byte[] response = REST_CLIENT.post()
                        .uri(endpoint)
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
                log.warn("[PLACES-OSM] Overpass mirror {} attempt {}/{} failed after {} ms: {}",
                        endpoint, attempt + 1, maxAttempts, elapsed, exception.getMessage());
                if (singleLoopback) {
                    if (attempt == 0 && elapsed < FAST_FAILURE_RETRY_MS) {
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
                if (!loopback) {
                    markMirrorTemporarilyDown(downKey);
                }
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
                waitForNominatimRateLimit();
                double[] coordinates = requestGeocode(query);
                saveGeocodeToCache(cacheKey, coordinates);
                return coordinates;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
            }
        }
    }

    private void waitForNominatimRateLimit() throws InterruptedException {
        synchronized (OpenStreetMapPlaceProvider.class) {
            long waitMillis = 1000 - (System.currentTimeMillis() - lastNominatimRequestAt);
            if (lastNominatimRequestAt > 0 && waitMillis > 0) Thread.sleep(waitMillis);
            lastNominatimRequestAt = System.currentTimeMillis();
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
            log.warn("[PLACES-OSM] Nominatim geocoding failed, trying Photon fallback: {}", exception.getMessage());
            return requestGeocodeWithPhoton(query);
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
            if (!hasVegetarianTag(tags) && !hasFoodIngredientShopTag(tags)) continue;

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
            terms.add("nhà hàng chay");
        } else if (category == PlaceCategory.SUPERMARKET) {
            terms.add("siêu thị");
            terms.add("siêu thị thực phẩm");
        } else if (category == PlaceCategory.GROCERY_STORE) {
            terms.add("cửa hàng tiện lợi");
            terms.add("cửa hàng thực phẩm");
            terms.add("thực phẩm chay");
        } else {
            terms.add("nhà hàng chay");
            terms.add("quán cơm chay");
            terms.add("siêu thị");
            terms.add("cửa hàng tiện lợi");
            terms.add("cửa hàng thực phẩm");
        }

        Map<String, PlaceResponse> unique = new LinkedHashMap<>();
        boolean nominatimFailed = false;
        for (String term : terms) {
            if (!nominatimFailed) {
                try {
                    for (PlaceResponse place : searchNominatim(term, latitude, longitude, radiusMeters, category)) {
                        unique.putIfAbsent(place.getPlaceId(), place);
                    }
                    continue;
                } catch (Exception exception) {
                    nominatimFailed = true;
                    log.warn("[PLACES-NOMINATIM] Search '{}' failed, trying Photon fallback: {}", term, exception.getMessage());
                }
            }
            try {
                for (PlaceResponse place : searchPhotonNearby(term, latitude, longitude, radiusMeters, category)) {
                    unique.putIfAbsent(place.getPlaceId(), place);
                }
            } catch (Exception exception) {
                log.warn("[PLACES-PHOTON] Search '{}' failed: {}", term, exception.getMessage());
            }
        }
        return unique.values().stream()
                .sorted(Comparator.comparing(PlaceResponse::getDistanceKm))
                .limit(45)
                .toList();
    }

    private List<PlaceResponse> searchNominatim(
            String term, double latitude, double longitude, int radiusMeters, PlaceCategory requestedCategory) throws Exception {
        double deltaLat = radiusMeters / 111_320.0;
        double deltaLon = radiusMeters / (111_320.0 * Math.max(0.2, Math.cos(Math.toRadians(latitude))));
        String viewbox = String.format(Locale.ROOT, "%f,%f,%f,%f",
                longitude - deltaLon, latitude + deltaLat, longitude + deltaLon, latitude - deltaLat);
        waitForNominatimRateLimit();
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
                if (exception instanceof org.springframework.web.client.ResourceAccessException) {
                    break;
                }
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

    private List<String> resolveOverpassEndpoints() {
        LinkedHashSet<String> endpoints = new LinkedHashSet<>();
        if (overpassUrl != null && !overpassUrl.isBlank()) {
            endpoints.add(overpassUrl.trim());
        }
        if (!isLoopbackEndpoint(overpassUrl) && overpassUrls != null && !overpassUrls.isBlank()) {
            for (String candidate : overpassUrls.split(",")) {
                String trimmed = candidate.trim();
                if (!trimmed.isEmpty()) endpoints.add(trimmed);
            }
        }
        return endpoints.isEmpty()
                ? List.of("https://overpass-api.de/api/interpreter")
                : endpoints.stream().limit(2).toList();
    }

    private static boolean isLoopbackEndpoint(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            String host = URI.create(url.trim()).getHost();
            return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isMirrorTemporarilyDown(String downKey) {
        try {
            return redisTemplate != null && redisTemplate.opsForValue().get(downKey) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void markMirrorTemporarilyDown(String downKey) {
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(downKey, "1", Duration.ofMinutes(3));
            }
        } catch (Exception ignored) {
        }
    }

    private String reverseGeocodeWithPhoton(double latitude, double longitude) {
        try {
            URI uri = buildPhotonReverseUri(latitude, longitude);
            byte[] response = REST_CLIENT.get().uri(uri).retrieve().body(byte[].class);
            if (response == null) return null;
            JsonNode root = objectMapper.readTree(response);
            JsonNode features = root.path("features");
            if (!features.isArray()) return null;
            if (features.isEmpty()) return "";
            return buildPhotonAddress(features.get(0).path("properties"));
        } catch (Exception exception) {
            log.warn("[PLACES-PHOTON] Reverse geocoding failed: {}", exception.getMessage());
            return null;
        }
    }

    private double[] requestGeocodeWithPhoton(String query) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(photonUrl)
                    .queryParam("q", query)
                    .queryParam("limit", 1)
                    .build().encode().toUri();
            byte[] response = REST_CLIENT.get().uri(uri).retrieve().body(byte[].class);
            if (response == null) throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
            JsonNode features = objectMapper.readTree(response).path("features");
            if (!features.isArray()) throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
            if (features.isEmpty()) return null;
            JsonNode coords = features.get(0).path("geometry").path("coordinates");
            if (!coords.isArray() || coords.size() < 2) return null;
            double lon = coords.get(0).asDouble(Double.NaN);
            double lat = coords.get(1).asDouble(Double.NaN);
            if (!Double.isFinite(lat) || !Double.isFinite(lon)) return null;
            return new double[]{lat, lon};
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("[PLACES-PHOTON] Geocoding failed: {}", exception.getMessage());
            throw new AppException(ErrorCode.PLACES_PROVIDER_UNAVAILABLE);
        }
    }

    private List<PlaceResponse> searchPhotonNearby(
            String term, double latitude, double longitude, int radiusMeters, PlaceCategory requestedCategory) throws Exception {
        URI uri = UriComponentsBuilder.fromUriString(photonUrl)
                .queryParam("q", term)
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("limit", "15")
                .build().encode().toUri();
        byte[] response = REST_CLIENT.get().uri(uri).retrieve().body(byte[].class);
        if (response == null) return List.of();
        JsonNode features = objectMapper.readTree(response).path("features");
        if (!features.isArray()) return List.of();

        List<PlaceResponse> places = new ArrayList<>();
        for (JsonNode feature : features) {
            PlaceResponse place = toPhotonPlace(feature, latitude, longitude, radiusMeters);
            if (place == null) continue;
            if (requestedCategory != PlaceCategory.ALL && place.getCategory() != requestedCategory) continue;
            places.add(place);
        }
        return places;
    }

    private PlaceResponse toPhotonPlace(JsonNode feature, double userLat, double userLon, int radiusMeters) {
        JsonNode coords = feature.path("geometry").path("coordinates");
        if (!coords.isArray() || coords.size() < 2) return null;
        double longitude = coords.get(0).asDouble(Double.NaN);
        double latitude = coords.get(1).asDouble(Double.NaN);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180
                || (latitude == 0 && longitude == 0)) {
            return null;
        }
        double exactDistanceKm = GeoUtils.calculateExactHaversineDistanceKm(userLat, userLon, latitude, longitude);
        if (exactDistanceKm * 1000.0 > radiusMeters) return null;

        JsonNode props = feature.path("properties");
        String name = props.path("name").asText("").trim();
        if (name.isBlank()) return null;
        String osmKey = props.path("osm_key").asText("");
        String osmValue = props.path("osm_value").asText("");
        PlaceCategory category = classifyNominatim(osmKey, osmValue);
        if (category == null) return null;

        String osmType = normalizePhotonOsmType(props.path("osm_type").asText(""));
        String osmId = props.path("osm_id").asText("");
        String placeId = !osmType.isBlank() && !osmId.isBlank()
                ? osmType + "/" + osmId
                : String.format(Locale.ROOT, "photon/%.5f,%.5f", latitude, longitude);
        String address = buildPhotonAddress(props);
        double roundedDistanceKm = Math.round(exactDistanceKm * 100.0) / 100.0;
        List<String> types = (!osmKey.isBlank() && !osmValue.isBlank())
                ? List.of(osmKey + "=" + osmValue)
                : List.of();

        return PlaceResponse.builder()
                .placeId(placeId)
                .name(name)
                .category(category)
                .address(address)
                .latitude(latitude)
                .longitude(longitude)
                .distanceKm(roundedDistanceKm)
                .distanceText(GeoUtils.formatDistanceText(roundedDistanceKm))
                .types(types)
                .isVerified(false)
                .verificationSource(null)
                .dataSource("OPENSTREETMAP_PHOTON")
                .openStreetMapUrl(buildOpenStreetMapUrl(osmType, osmId, latitude, longitude))
                .attribution(OSM_ATTRIBUTION)
                .build();
    }

    private URI buildPhotonReverseUri(double latitude, double longitude) {
        String base = photonUrl == null ? "https://photon.komoot.io/api/" : photonUrl.trim();
        String reverseBase = base.endsWith("/api/")
                ? base.substring(0, base.length() - 5) + "/reverse"
                : base.endsWith("/api")
                        ? base.substring(0, base.length() - 4) + "/reverse"
                        : base.replaceAll("/+$", "") + "/reverse";
        return UriComponentsBuilder.fromUriString(reverseBase)
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .build().encode().toUri();
    }

    private static String normalizePhotonOsmType(String raw) {
        if ("N".equalsIgnoreCase(raw)) return "node";
        if ("W".equalsIgnoreCase(raw)) return "way";
        if ("R".equalsIgnoreCase(raw)) return "relation";
        String lower = normalize(raw);
        return Set.of("node", "way", "relation").contains(lower) ? lower : "";
    }

    private static String buildPhotonAddress(JsonNode props) {
        if (props == null || !props.isObject()) return "";
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        String houseNumber = props.path("housenumber").asText("").trim();
        String street = props.path("street").asText("").trim();
        if (!houseNumber.isBlank() && !street.isBlank()) {
            parts.add(houseNumber + " " + street);
        } else if (!street.isBlank()) {
            parts.add(street);
        }
        String name = props.path("name").asText("").trim();
        if (parts.isEmpty() && !name.isBlank()) {
            parts.add(name);
        }
        for (String field : List.of("locality", "suburb", "district", "city", "state", "country")) {
            String value = props.path(field).asText("").trim();
            if (!value.isBlank()) parts.add(value);
        }
        return String.join(", ", parts);
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
        if (Set.of("supermarket", "department_store", "mall").contains(normalizedType)) {
            return PlaceCategory.SUPERMARKET;
        }
        if (Set.of(
                "convenience", "greengrocer", "health_food", "organic", "farm",
                "deli", "general", "food", "bakery", "herbalist", "spices",
                "grocery", "minimarket", "mini_market", "variety_store"
        ).contains(normalizedType)) {
            return PlaceCategory.GROCERY_STORE;
        }
        if ("amenity".equals(normalizedClass) && normalizedType.isBlank()) {
            return PlaceCategory.RESTAURANT;
        }
        return null;
    }

    private String buildOverpassQuery(double latitude, double longitude, int radiusMeters) {
        String around = "(around:" + radiusMeters + "," + latitude + "," + longitude + ")";
        return "[out:json][timeout:7];("
                + "nwr[\"cuisine\"~\"vegetarian|vegan\",i]" + around + ";"
                + "nwr[\"diet:vegetarian\"~\"^(yes|only)$\",i]" + around + ";"
                + "nwr[\"diet:vegan\"~\"^(yes|only)$\",i]" + around + ";"
                + "nwr[\"shop\"~\"^(supermarket|convenience|greengrocer|health_food|organic|grocery|deli)$\",i]" + around + ";"
                + ");out center tags;";
    }

    private static final Set<String> FOOD_INGREDIENT_SHOPS = Set.of(
            "supermarket", "convenience", "greengrocer", "health_food", "organic",
            "grocery", "deli", "minimarket", "mini_market", "farm", "herbalist", "spices"
    );

    private static boolean hasFoodIngredientShopTag(Map<String, String> tags) {
        return FOOD_INGREDIENT_SHOPS.contains(normalize(tags.get("shop")));
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
        if (Set.of("supermarket", "department_store", "mall").contains(shop)) {
            return PlaceCategory.SUPERMARKET;
        }
        if (FOOD_INGREDIENT_SHOPS.contains(shop)) {
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

    private static String stripAccents(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'd')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static Comparator<PlaceResponse> foodRelevanceComparator(String foodQuery) {
        if (foodQuery == null || foodQuery.isBlank()) {
            return Comparator.comparing(PlaceResponse::getDistanceKm);
        }
        String normalizedFull = stripAccents(foodQuery);
        List<String> tokens = Arrays.stream(normalizedFull.split("[\\s,;:/()\\-]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2 && !Set.of("mon", "chay", "thuan", "tu", "nau", "voi").contains(token))
                .toList();
        return Comparator.<PlaceResponse>comparingInt(place -> -foodMatchScore(place, normalizedFull, tokens))
                .thenComparing(PlaceResponse::getDistanceKm);
    }

    private static int foodMatchScore(PlaceResponse place, String normalizedFull, List<String> tokens) {
        String haystack = stripAccents(place.getName() + " " + place.getAddress() + " "
                + (place.getTypes() == null ? "" : String.join(" ", place.getTypes())));
        int score = place.isVerified() ? 2 : 0;
        if (place.getCategory() == PlaceCategory.GROCERY_STORE || place.getCategory() == PlaceCategory.SUPERMARKET) {
            score += 1;
        }
        if (!normalizedFull.isEmpty() && haystack.contains(normalizedFull)) {
            score += 20;
        }
        for (String token : tokens) {
            if (haystack.contains(token)) {
                score += 6;
            }
        }
        return score;
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
