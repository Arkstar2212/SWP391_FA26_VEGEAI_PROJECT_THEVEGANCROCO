package com.vegeai.backend.modules.place.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.dto.PlaceResponse;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenStreetMapPlaceProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesTaggedVegetarianPlaceWithoutInventingGoogleMetadata() throws Exception {
        String response = """
                {
                  "elements": [
                    {
                      "type": "node",
                      "id": 123,
                      "lat": 10.78,
                      "lon": 106.70,
                      "tags": {
                        "name": "Quan Chay",
                        "amenity": "restaurant",
                        "cuisine": "vegetarian",
                        "addr:street": "Main Street"
                      }
                    },
                    {
                      "type": "node",
                      "id": 124,
                      "lat": 10.79,
                      "lon": 106.70,
                      "tags": {
                        "name": "Regular Restaurant",
                        "amenity": "restaurant"
                      }
                    }
                  ]
                }
                """;

        List<PlaceResponse> places = OpenStreetMapPlaceProvider.parseOverpassResponse(
                response, 10.78, 106.70, objectMapper);

        assertEquals(1, places.size());
        PlaceResponse place = places.get(0);
        assertEquals("node/123", place.getPlaceId());
        assertEquals(PlaceCategory.RESTAURANT, place.getCategory());
        assertEquals("Main Street", place.getAddress());
        assertEquals("https://www.openstreetmap.org/node/123", place.getOpenStreetMapUrl());
        assertEquals("OPENSTREETMAP", place.getDataSource());
        assertTrue(place.isVerified());
        assertNull(place.getRating());
        assertNull(place.getUserRatingsTotal());
        assertNull(place.getIsOpenNow());
    }

    @Test
    void keepsVegetarianOptionAsCandidateButNotExclusiveVerification() throws Exception {
        String response = """
                {"elements":[{"type":"node","id":9,"lat":10.78,"lon":106.70,
                  "tags":{"name":"Mixed Menu","amenity":"restaurant","diet:vegetarian":"yes"}}]}
                """;

        List<PlaceResponse> places = OpenStreetMapPlaceProvider.parseOverpassResponse(
                response, 10.78, 106.70, objectMapper);

        assertEquals(1, places.size());
        assertFalse(places.get(0).isVerified());
    }

    @Test
    void doesNotVerifyMixedCuisineAsVegetarianOnly() throws Exception {
        String response = """
                {"elements":[{"type":"node","id":10,"lat":10.78,"lon":106.70,
                  "tags":{"name":"Mixed Cuisine","amenity":"restaurant","cuisine":"vietnamese;vegetarian"}}]}
                """;

        List<PlaceResponse> places = OpenStreetMapPlaceProvider.parseOverpassResponse(
                response, 10.78, 106.70, objectMapper);

        assertEquals(1, places.size());
        assertFalse(places.get(0).isVerified());
    }

    @Test
    void geocodesSearchAndReadsOverpassJsonWithOctetStreamContentType() throws Exception {
        byte[] body = """
                {"elements":[{"type":"node","id":11,"lat":10.78,"lon":106.70,
                  "tags":{"name":"OSM Vegetarian","amenity":"restaurant","cuisine":"vegetarian"}}]}
                """.getBytes(StandardCharsets.UTF_8);
        AtomicInteger geocodeRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/interpreter", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.createContext("/search", exchange -> {
            geocodeRequests.incrementAndGet();
            byte[] geocoded = "[{\"lat\":\"10.78\",\"lon\":\"106.70\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, geocoded.length);
            try (var output = exchange.getResponseBody()) {
                output.write(geocoded);
            }
        });
        server.start();
        try {
            Map<String, String> cache = new ConcurrentHashMap<>();
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            @SuppressWarnings("unchecked")
            ValueOperations<String, String> values = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(values);
            when(values.get(anyString())).thenAnswer(invocation -> cache.get(invocation.getArgument(0)));
            doAnswer(invocation -> {
                cache.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(values).set(anyString(), anyString(), any(Duration.class));

            OpenStreetMapPlaceProvider provider = new OpenStreetMapPlaceProvider(objectMapper, redis);
            ReflectionTestUtils.setField(provider, "overpassUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/interpreter");
            ReflectionTestUtils.setField(provider, "nominatimUrl", "http://127.0.0.1:" + server.getAddress().getPort());

            List<PlaceResponse> places = provider.searchVegetarianPlaces(
                    "Test district", null, null, 5000, PlaceCategory.RESTAURANT);
            List<PlaceResponse> cachedPlaces = provider.searchVegetarianPlaces(
                    "Test district", null, null, 5000, PlaceCategory.RESTAURANT);

            assertEquals(1, places.size());
            assertEquals("OSM Vegetarian", places.get(0).getName());
            assertTrue(places.get(0).isVerified());
            assertEquals(1, cachedPlaces.size());
            assertEquals(1, geocodeRequests.get(), "Repeated place searches should use the cached Nominatim result.");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void suggestShopsForFoodRanksMatchingVegetarianShopsFirst() throws Exception {
        byte[] body = """
                {"elements":[
                  {"type":"node","id":201,"lat":10.7801,"lon":106.7001,
                    "tags":{"name":"Quán Cà Phê Thanh Bình","amenity":"cafe","diet:vegan":"only"}},
                  {"type":"node","id":202,"lat":10.7850,"lon":106.7050,
                    "tags":{"name":"Siêu Thị Nấm & Rau Củ Hữu Cơ Chay","shop":"supermarket","diet:vegan":"only"}}
                ]}
                """.getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/interpreter", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            @SuppressWarnings("unchecked")
            ValueOperations<String, String> values = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(values);

            OpenStreetMapPlaceProvider provider = new OpenStreetMapPlaceProvider(objectMapper, redis);
            ReflectionTestUtils.setField(provider, "overpassUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/interpreter");

            List<PlaceResponse> suggested = provider.suggestShopsForFood("Nấm bào ngư", 10.78, 106.70, 5000);
            assertEquals(2, suggested.size());
            assertEquals("Siêu Thị Nấm & Rau Củ Hữu Cơ Chay", suggested.get(0).getName());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToPhotonWhenOverpassAndNominatimFail() throws Exception {
        byte[] photonNearbyBody = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {
                        "osm_type": "N",
                        "osm_id": 13533560501,
                        "osm_key": "amenity",
                        "osm_value": "restaurant",
                        "housenumber": "61",
                        "name": "Chay Diệu My",
                        "street": "Đề Thám",
                        "district": "Cầu Ông Lãnh",
                        "city": "Thành phố Hồ Chí Minh"
                      },
                      "geometry": { "type": "Point", "coordinates": [106.6954529, 10.7638908] }
                    },
                    {
                      "type": "Feature",
                      "properties": {
                        "osm_type": "N",
                        "osm_id": 999999,
                        "osm_key": "amenity",
                        "osm_value": "restaurant",
                        "name": "Too Far Place"
                      },
                      "geometry": { "type": "Point", "coordinates": [105.80, 21.00] }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        byte[] photonReverseBody = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {
                        "name": "UBND Thành phố Hồ Chí Minh",
                        "district": "Sài Gòn",
                        "city": "Thành phố Hồ Chí Minh"
                      },
                      "geometry": { "type": "Point", "coordinates": [106.7009, 10.7769] }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/interpreter", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(502, -1);
            exchange.close();
        });
        server.createContext("/nominatim", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.createContext("/api/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, photonNearbyBody.length);
            try (var output = exchange.getResponseBody()) {
                output.write(photonNearbyBody);
            }
        });
        server.createContext("/reverse", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, photonReverseBody.length);
            try (var output = exchange.getResponseBody()) {
                output.write(photonReverseBody);
            }
        });
        server.start();
        try {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            @SuppressWarnings("unchecked")
            ValueOperations<String, String> values = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(values);

            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            OpenStreetMapPlaceProvider provider = new OpenStreetMapPlaceProvider(objectMapper, redis);
            ReflectionTestUtils.setField(provider, "overpassUrl", baseUrl + "/interpreter");
            ReflectionTestUtils.setField(provider, "nominatimUrl", baseUrl + "/nominatim");
            ReflectionTestUtils.setField(provider, "photonUrl", baseUrl + "/api/");

            List<PlaceResponse> places = provider.findNearbyVegetarianPlaces(10.7769, 106.7009, 5000, PlaceCategory.RESTAURANT, null);
            assertEquals(1, places.size());
            PlaceResponse first = places.get(0);
            assertEquals("Chay Diệu My", first.getName());
            assertEquals("node/13533560501", first.getPlaceId());
            assertEquals("OPENSTREETMAP_PHOTON", first.getDataSource());
            assertFalse(first.isVerified());

            String address = provider.reverseGeocode(10.7769, 106.7009);
            assertTrue(address.contains("UBND Thành phố Hồ Chí Minh"));
            assertTrue(address.contains("Thành phố Hồ Chí Minh"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void includesSupermarketsAndConvenienceStoresForVegetarianIngredientsSupply() throws Exception {
        String response = """
                {
                  "elements": [
                    {
                      "type": "node",
                      "id": 301,
                      "lat": 10.777,
                      "lon": 106.701,
                      "tags": {
                        "name": "Circle K Lê Thánh Tôn",
                        "shop": "convenience",
                        "addr:street": "Lê Thánh Tôn"
                      }
                    },
                    {
                      "type": "node",
                      "id": 302,
                      "lat": 10.778,
                      "lon": 106.702,
                      "tags": {
                        "name": "Bách hóa XANH",
                        "shop": "supermarket"
                      }
                    },
                    {
                      "type": "node",
                      "id": 303,
                      "lat": 10.779,
                      "lon": 106.703,
                      "tags": {
                        "name": "Regular Diner",
                        "amenity": "restaurant"
                      }
                    }
                  ]
                }
                """;

        List<PlaceResponse> places = OpenStreetMapPlaceProvider.parseOverpassResponse(
                response, 10.7769, 106.7009, objectMapper);

        assertEquals(2, places.size());
        assertEquals("Circle K Lê Thánh Tôn", places.get(0).getName());
        assertEquals(PlaceCategory.GROCERY_STORE, places.get(0).getCategory());
        assertFalse(places.get(0).isVerified());

        assertEquals("Bách hóa XANH", places.get(1).getName());
        assertEquals(PlaceCategory.SUPERMARKET, places.get(1).getCategory());
        assertFalse(places.get(1).isVerified());
    }

}
