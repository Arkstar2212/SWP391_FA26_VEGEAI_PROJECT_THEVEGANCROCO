package com.vegeai.backend.modules.place.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.dto.PlaceResponse;
import com.vegeai.backend.modules.place.util.GeoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;

@Service
public class VerifiedPlacesService {
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;
    private final RestClient client;
    private final List<String> endpoints;
    private final String photonUrl;
    private long lastGeocodeNanos;
    private static final Set<String> EATERIES = Set.of("restaurant", "cafe", "fast_food", "food_court");

    public VerifiedPlacesService(ObjectMapper mapper, StringRedisTemplate redis,
            @Value("${app.osm.overpass-urls:https://overpass.private.coffee/api/interpreter,https://overpass-api.de/api/interpreter}") String urls,
            @Value("${app.osm.photon-url:https://photon.komoot.io/api/}") String photonUrl,
            @Value("${app.osm.user-agent:VEGEAI/1.0 (+https://github.com/cudatbusua/SWP391_VEGEAI_FRONTEND)}") String userAgent) {
        this.mapper=mapper; this.redis=redis; this.photonUrl=photonUrl;
        endpoints=Arrays.stream(urls.split(",")).map(String::trim).filter(s -> !s.isEmpty()).limit(2).toList();
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3)); factory.setReadTimeout(Duration.ofSeconds(12));
        client=RestClient.builder().requestFactory(factory).defaultHeader("User-Agent",userAgent)
                .defaultHeader("Accept",MediaType.APPLICATION_JSON_VALUE).build();
    }

    public List<PlaceResponse> nearby(double lat, double lng, int radius) {
        validate(lat,lng,radius);
        String key=key("nearby",Double.toString(lat)+":"+lng+":"+radius);
        String cached=read(key);
        if(cached!=null) try { return mapper.readValue(cached,new TypeReference<List<PlaceResponse>>(){}); }
        catch(Exception ignored) { /* Ignore stale or incompatible cache entries. */ }
        String around=String.format(Locale.ROOT,"(around:%d,%.7f,%.7f)",radius,lat,lng);
        String query="[out:json][timeout:8];(";
        for(String selector:List.of("[\"diet:vegetarian\"=\"only\"]", "[\"diet:vegan\"=\"only\"]", "[cuisine~\"vegetarian|vegan\",i]"))
            query+="nwr"+around+"[amenity~\"^(restaurant|cafe|fast_food|food_court)$\"]"+selector+";";
        query+=");out center;";
        JsonNode root=null;
        for(String endpoint:endpoints) {
            String unavailableKey=key("provider-unavailable",endpoint);
            if(read(unavailableKey)!=null) continue;
            try {
                var form=new LinkedMultiValueMap<String,String>(); form.add("data",query);
                JsonNode candidate=mapper.readTree(client.post().uri(endpoint)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String.class));
                if(candidate==null || !candidate.path("elements").isArray() || candidate.hasNonNull("remark")) {
                    write(unavailableKey,"1",Duration.ofSeconds(30));
                    continue;
                }
                root=candidate; break;
            } catch(Exception ignored) {
                // Pause an unavailable shared provider across coordinates and backend instances.
                write(unavailableKey,"1",Duration.ofSeconds(30));
            }
        }
        if(root==null) throw new AppException(ErrorCode.PLACES_UNAVAILABLE);
        List<PlaceResponse> places=parse(root,lat,lng,radius);
        try { write(key,mapper.writeValueAsString(places),Duration.ofMinutes(15)); } catch(Exception ignored) { }
        return places;
    }

    public List<PlaceResponse> search(String query, Double lat, Double lng, int radius) {
        if(query==null || query.isBlank() || query.trim().length()>200 || (lat==null)!=(lng==null))
            throw new AppException(ErrorCode.VALIDATION_ERROR,"Nhập khu vực hoặc tên quán hợp lệ, và cung cấp đủ lat/lng nếu tìm quanh vị trí.");
        query=query.trim();
        if(lat!=null) {
            String text=normalize(query);
            return nearby(lat,lng,radius).stream().filter(p -> normalize(p.getName()+" "+p.getAddress()).contains(text)).toList();
        }
        if(radius<500 || radius>50000) throw new AppException(ErrorCode.VALIDATION_ERROR,"Bán kính phải từ 500 đến 50000 mét.");
        double[] point=geocode(query);
        return point==null ? List.of() : nearby(point[0],point[1],radius);
    }

    private synchronized double[] geocode(String query) {
        String key=key("area",normalize(query));
        String cached=read(key);
        if(cached!=null) try {
            double[] point=mapper.readValue(cached,double[].class);
            if(point.length==0) return null;
            if(point.length==2 && validPoint(point[0],point[1])) return point;
        } catch(Exception ignored) { }
        // Bound public geocoder usage across instances when Redis is available.
        Boolean reserved=null;
        try { reserved=redis.opsForValue().setIfAbsent("places:osm:geocode-rate","1",Duration.ofSeconds(1)); }
        catch(Exception ignored) { }
        long now=System.nanoTime();
        if(Boolean.FALSE.equals(reserved) || (reserved==null && lastGeocodeNanos!=0 && now-lastGeocodeNanos<1_000_000_000L))
            throw new AppException(ErrorCode.TOO_MANY_REQUESTS,"Vui lòng chờ một giây trước khi tìm khu vực khác.");
        lastGeocodeNanos=now;
        try {
            var uri=UriComponentsBuilder.fromUriString(photonUrl).queryParam("q","{query}").queryParam("limit",1)
                    .encode().buildAndExpand(query).toUri();
            JsonNode root=mapper.readTree(client.get().uri(uri).retrieve().body(String.class));
            if(root==null || !root.path("features").isArray()) throw new AppException(ErrorCode.PLACES_INVALID_RESPONSE);
            if(root.path("features").isEmpty()) { write(key,"[]",Duration.ofMinutes(5)); return null; }
            JsonNode coords=root.path("features").get(0).path("geometry").path("coordinates");
            if(!coords.isArray() || coords.size()<2 || !coords.get(0).isNumber() || !coords.get(1).isNumber())
                throw new AppException(ErrorCode.PLACES_INVALID_RESPONSE);
            double[] point={coords.get(1).asDouble(),coords.get(0).asDouble()};
            validate(point[0],point[1],500);
            write(key,mapper.writeValueAsString(point),Duration.ofHours(24));
            return point;
        } catch(AppException ex) { throw ex; }
        catch(Exception ex) { throw new AppException(ErrorCode.PLACES_UNAVAILABLE); }
    }

    private List<PlaceResponse> parse(JsonNode root, double lat, double lng, int radius) {
        Map<String,PlaceResponse> places=new LinkedHashMap<>();
        for(JsonNode element:root.path("elements")) {
            JsonNode tags=element.path("tags");
            if(!EATERIES.contains(tags.path("amenity").asText()) || !exclusive(tags)) continue;
            String name=tags.path("name").asText(tags.path("name:vi").asText()).trim();
            String type=element.path("type").asText();
            if(name.isEmpty() || !Set.of("node","way","relation").contains(type) || !element.path("id").canConvertToLong() || element.path("id").asLong()<=0) continue;
            JsonNode location="node".equals(type) ? element : element.path("center");
            if(!location.path("lat").isNumber() || !location.path("lon").isNumber()) continue;
            double pLat=location.path("lat").asDouble(), pLng=location.path("lon").asDouble();
            if(!validPoint(pLat,pLng)) continue;
            // GeoUtils rounds to 10 m; use an unrounded distance for the radius boundary.
            double distance=GeoUtils.calculateExactHaversineDistanceKm(lat,lng,pLat,pLng);
            if(distance*1000>radius) continue;
            String id="osm_"+type+"_"+element.path("id").asLong();
            List<String> address=new ArrayList<>();
            if(tags.hasNonNull("addr:full")) address.add(tags.path("addr:full").asText());
            else for(String field:List.of("addr:housenumber","addr:street","addr:suburb","addr:district","addr:city"))
                if(!tags.path(field).asText().isBlank()) address.add(tags.path(field).asText());
            List<String> types=new ArrayList<>();
            for(String field:List.of("amenity","cuisine","diet:vegetarian","diet:vegan"))
                if(tags.hasNonNull(field)) types.add(field+"="+tags.path(field).asText());
            places.putIfAbsent(id,PlaceResponse.builder().placeId(id).name(name).category(PlaceCategory.RESTAURANT)
                    .address(String.join(", ",address)).latitude(pLat).longitude(pLng).distanceKm(distance)
                    .distanceText(GeoUtils.formatDistanceText(distance)).types(types).isVerified(true)
                    .phoneNumber(tags.path("contact:phone").asText(tags.path("phone").asText(null)))
                    .openStreetMapUrl("https://www.openstreetmap.org/"+type+"/"+element.path("id").asLong())
                    .googleMapsUrl(GeoUtils.buildGoogleMapsDirectionsUrl(pLat,pLng,null))
                    .attribution("© OpenStreetMap contributors (ODbL); dietary classification from community tags")
                    .build());
        }
        return places.values().stream().sorted(Comparator.comparingDouble(PlaceResponse::getDistanceKm)).toList();
    }

    private boolean exclusive(JsonNode tags) {
        if("only".equalsIgnoreCase(tags.path("diet:vegetarian").asText()) || "only".equalsIgnoreCase(tags.path("diet:vegan").asText())) return true;
        String cuisine=tags.path("cuisine").asText();
        if(cuisine.isBlank()) return false;
        return Arrays.stream(cuisine.split("[;,]",-1)).map(String::trim)
                .allMatch(s -> s.equalsIgnoreCase("vegetarian") || s.equalsIgnoreCase("vegan"));
    }
    private void validate(double lat,double lng,int radius) {
        if(!validPoint(lat,lng) || radius<500 || radius>50000)
            throw new AppException(ErrorCode.VALIDATION_ERROR,"Tọa độ không hợp lệ hoặc bán kính ngoài khoảng 500–50000 mét.");
    }
    private boolean validPoint(double lat,double lng) { return Double.isFinite(lat)&&Double.isFinite(lng)&&Math.abs(lat)<=90&&Math.abs(lng)<=180; }
    private String normalize(String value) { return Normalizer.normalize(value.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d').trim(); }
    private String key(String kind,String value) {
        try { return "places:osm:v1:"+kind+":"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    private String read(String key) { try { return redis.opsForValue().get(key); } catch(Exception ignored) { return null; } }
    private void write(String key,String value,Duration ttl) { try { redis.opsForValue().set(key,value,ttl); } catch(Exception ignored) { } }
}
