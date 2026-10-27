package com.vegeai.backend.modules.place.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class VerifiedPlacesApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    @MockBean(answer=org.mockito.Answers.RETURNS_DEEP_STUBS) StringRedisTemplate redis;
    static volatile String overpassBody="{\"elements\":[]}";
    static volatile String geocoderBody="{\"features\":[]}";
    static volatile boolean failAll;
    static volatile String providerQuery;
    static final AtomicInteger calls=new AtomicInteger();
    static final HttpServer server=start();
    static HttpServer start() {
        try {
            var s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            s.createContext("/",exchange -> {
                try(exchange) {
                    calls.incrementAndGet();
                    providerQuery=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                    boolean geocoder=exchange.getRequestURI().getPath().equals("/geocode");
                    String body=geocoder ? geocoderBody : overpassBody;
                    int status=failAll || exchange.getRequestURI().getPath().equals("/bad") ? 503 : 200;
                    exchange.getResponseHeaders().set("Content-Type","application/json");
                    byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
                }
            });
            s.start();Runtime.getRuntime().addShutdownHook(new Thread(() -> s.stop(0)));return s;
        } catch(Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        p.add("app.osm.overpass-urls",()->base+"/bad,"+base+"/overpass");
        p.add("app.osm.photon-url",()->base+"/geocode");
    }
    @BeforeEach void setup() {
        failAll=false;calls.set(0);overpassBody="{\"elements\":[]}";geocoderBody="{\"features\":[]}";
        Map<String,String> cache=new HashMap<>();
        var values=redis.opsForValue();
        when(values.get(anyString())).thenAnswer(i -> cache.get(i.getArgument(0)));
        doAnswer(i -> {cache.put(i.getArgument(0),i.getArgument(1));return null;})
                .when(values).set(anyString(),anyString(),any(java.time.Duration.class));
        when(values.setIfAbsent(anyString(),anyString(),any(java.time.Duration.class))).thenReturn(true);
    }
    String endpoint() { return "/api/places/verified/nearby?lat=10&lng=106&radius=1000"; }
    Map<String,Object> node(long id,double lat,String cuisine,String diet) {
        var tags=new HashMap<String,String>();tags.put("name","Quán fixture "+id);tags.put("amenity","restaurant");
        tags.put("cuisine",cuisine);tags.put("diet:vegetarian",diet);tags.put("addr:street","Đường thử nghiệm");
        return Map.of("type","node","id",id,"lat",lat,"lon",106,"tags",tags);
    }
    @Test void guestReceivesRealShapedStrictlyFilteredSortedPlacesAndUnknownRatingsStayNull()throws Exception {
        var valid=node(1,10.001,"vietnamese","only");
        var way=Map.of("type","way","id",2,"center",Map.of("lat",10.002,"lon",106),"tags",Map.of("name","Vegan fixture","amenity","cafe","cuisine","vegan"));
        overpassBody=mapper.writeValueAsString(Map.of("elements",List.of(way,valid,valid,
                node(3,10.0001,"vietnamese","yes"),node(4,10.0001,"vegetarian;seafood","yes"),
                node(5,11,"vegan","only"),Map.of("type","node","id",6,"tags",Map.of("name","No coordinates","amenity","restaurant","cuisine","vegan")))));
        var result=mvc.perform(get(endpoint())).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].placeId").value("osm_node_1"))
                .andExpect(jsonPath("$.data[1].openStreetMapUrl").value("https://www.openstreetmap.org/way/2"))
                .andExpect(jsonPath("$.data[0].isVerified").value(true)).andExpect(jsonPath("$.data[0].rating").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("OpenStreetMap","diet:vegetarian=only").doesNotContain("mock_");
        int previous=calls.get();mvc.perform(get(endpoint())).andExpect(status().isOk());assertThat(calls.get()).isEqualTo(previous);
        assertThat(providerQuery).contains("data=","around");
    }
    @Test void emptyResultsAreNotReplacedWithSamplesAndProviderOutagesAre503()throws Exception {
        mvc.perform(get(endpoint())).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        failAll=true;
        mvc.perform(get(endpoint().replace("1000","2000"))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(2601));
    }
    @Test void failedProvidersPauseAcrossLocationsButSuccessfulCachedPlacesRemainReadable()throws Exception {
        overpassBody=mapper.writeValueAsString(Map.of("elements",List.of(node(1,10.001,"vegan","only"))));
        mvc.perform(get(endpoint())).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        failAll=true;
        mvc.perform(get(endpoint().replace("1000","2000"))).andExpect(status().isServiceUnavailable());
        int previous=calls.get();
        mvc.perform(get(endpoint().replace("1000","3000"))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(2601));
        mvc.perform(get(endpoint())).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].placeId").value("osm_node_1"));
        assertThat(calls.get()).isEqualTo(previous);
    }
    @Test void invalidCoordinatesRadiusAndHalfSpecifiedLocationFailBeforeProviderCalls()throws Exception {
        for(String params:List.of("lat=91&lng=106","lat=NaN&lng=106","lat=10&lng=181","lat=10&lng=106&radius=0","lat=10&lng=106&radius=50001"))
            mvc.perform(get("/api/places/verified/nearby?"+params)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/places/verified/search?query=Area&lat=10")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/places/verified/search").param("query"," ")).andExpect(status().isBadRequest());
        assertThat(calls.get()).isZero();
    }
    @Test void areaSearchUsesGeocoderCoordinatesAndCachesGeocoding()throws Exception {
        geocoderBody="{\"features\":[{\"geometry\":{\"coordinates\":[106,10]}}]}";
        overpassBody=mapper.writeValueAsString(Map.of("elements",List.of(node(1,10.001,"vegan","only"))));
        mvc.perform(get("/api/places/verified/search").param("query","Quận thử nghiệm"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        int previous=calls.get();
        mvc.perform(get("/api/places/verified/search").param("query","Quận thử nghiệm")).andExpect(status().isOk());
        assertThat(calls.get()).isEqualTo(previous);
    }
    @Test void unknownAreaReturnsEmptyAndDoesNotFallBackToSaigon()throws Exception {
        mvc.perform(get("/api/places/verified/search?query=UnknownArea")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        int previous=calls.get();
        mvc.perform(get("/api/places/verified/search?query=UnknownArea")).andExpect(status().isOk());
        assertThat(calls.get()).isEqualTo(previous);
    }
}
