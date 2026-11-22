package com.vegeai.backend.modules.place.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.place.dto.CategoryMetadataDto;
import com.vegeai.backend.modules.place.dto.AreaSuggestion;
import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.dto.PlaceResponse;
import com.vegeai.backend.modules.place.service.VerifiedPlacesService;
import com.vegeai.backend.modules.place.service.PlaceProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "10. OpenStreetMap Places", description = "Tìm quán có thông tin món chay và cửa hàng thực phẩm trên OpenStreetMap.")
public class PlaceController {

    private final PlaceProvider placeProvider;
    private final VerifiedPlacesService verifiedPlacesService;

    @GetMapping("/reverse-geocode")
    public ApiResponse<Map<String, String>> reverseGeocode(@RequestParam double lat, @RequestParam double lng) {
        return ApiResponse.success(Map.of("address", placeProvider.reverseGeocode(lat, lng)));
    }

    @GetMapping("/resolve-area")
    @Operation(summary = "Resolve a typed area to map coordinates, even when no vegetarian places were found")
    public ApiResponse<Map<String, Double>> resolveArea(@RequestParam String query) {
        double[] point = placeProvider.resolveArea(query);
        return ApiResponse.success(point == null ? null : Map.of("lat", point[0], "lon", point[1]));
    }

    @GetMapping("/suggest-areas")
    @Operation(summary = "Suggest real OpenStreetMap locations while typing an area")
    public ApiResponse<List<AreaSuggestion>> suggestAreas(@RequestParam String query,
            @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng) {
        return ApiResponse.success(placeProvider.suggestAreas(query, lat, lng));
    }

    @GetMapping("/verified/nearby")
    @Operation(summary = "Find nearby restaurants with explicit vegetarian-only or vegan-only OSM tags")
    public ApiResponse<List<PlaceResponse>> verifiedNearby(@RequestParam double lat, @RequestParam double lng,
            @RequestParam(defaultValue = "5000") int radius) {
        return ApiResponse.success(verifiedPlacesService.nearby(lat, lng, radius));
    }

    @GetMapping("/verified/search")
    @Operation(summary = "Search vegetarian restaurants by area or by name near supplied coordinates")
    public ApiResponse<List<PlaceResponse>> verifiedSearch(@RequestParam String query,
            @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "5000") int radius) {
        return ApiResponse.success(verifiedPlacesService.search(query, lat, lng, radius));
    }

    @GetMapping("/nearby")
    @Operation(
            summary = "Tìm quán chay và cửa hàng thực phẩm quanh tọa độ hiện tại",
            description = "Trả về địa điểm thật trên OpenStreetMap trong bán kính mặc định 5km. Cửa hàng thực phẩm không mặc định là cửa hàng chay; kết quả có thể chưa đầy đủ."
    )
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> getNearbyVegetarianPlaces(
            @Parameter(description = "Vĩ độ của người dùng (Latitude)", example = "10.7769", required = true)
            @RequestParam("lat") double lat,

            @Parameter(description = "Kinh độ của người dùng (Longitude)", example = "106.7009", required = true)
            @RequestParam("lng") double lng,

            @Parameter(description = "Bán kính tìm kiếm tính bằng mét (mặc định 5000 = 5km)", example = "5000")
            @RequestParam(value = "radius", defaultValue = "5000") int radius,

            @Parameter(description = "Phân loại địa điểm (ALL: Tất cả, RESTAURANT: Quán ăn/Nhà hàng, SUPERMARKET: Siêu thị, GROCERY_STORE: Cửa hàng nguyên liệu)", example = "ALL")
            @RequestParam(value = "category", defaultValue = "ALL") PlaceCategory category,

            @Parameter(description = "Từ khóa tìm kiếm bổ sung (ví dụ: 'buffet', 'đậu phụ', 'lẩu')", example = "buffet")
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        log.info("[PLACES] Yêu cầu tìm kiếm quanh lat={}, lng={}, radius={}m, category={}, keyword='{}'",
                lat, lng, radius, category, keyword);

        List<PlaceResponse> places = placeProvider.findNearbyVegetarianPlaces(lat, lng, radius, category, keyword);

        return ResponseEntity.ok(ApiResponse.<List<PlaceResponse>>builder()
                .success(true)
                .code(200)
                .message("Tìm thấy " + places.size() + " địa điểm quanh bạn trên OpenStreetMap")
                .data(places)
                .build());
    }

    @GetMapping("/search")
    @Operation(
            summary = "Tìm địa điểm có thẻ chay theo địa danh",
            description = "Nếu có lat/lng, tìm quanh tọa độ đó; nếu không, địa danh trong query được định vị bằng Nominatim rồi tìm trong bán kính. Chỉ có dữ liệu được gắn thẻ vegetarian/vegan trên OpenStreetMap."
    )
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> searchVegetarianPlaces(
            @Parameter(description = "Từ khóa tìm kiếm (VD: 'Cơm chay Quận 1', 'Đại An')", example = "Cơm chay Quận 1", required = true)
            @RequestParam("query") String query,

            @Parameter(description = "Vĩ độ người dùng (tùy chọn)", example = "10.7769")
            @RequestParam(value = "lat", required = false) Double lat,

            @Parameter(description = "Kinh độ người dùng (tùy chọn)", example = "106.7009")
            @RequestParam(value = "lng", required = false) Double lng,

            @Parameter(description = "Bán kính tìm kiếm tính bằng mét (mặc định 10000 = 10km)", example = "10000")
            @RequestParam(value = "radius", defaultValue = "10000") int radius,

            @Parameter(description = "Lọc danh mục (ALL, RESTAURANT, SUPERMARKET, GROCERY_STORE)", example = "RESTAURANT")
            @RequestParam(value = "category", defaultValue = "ALL") PlaceCategory category
    ) {
        log.info("[PLACES] Tìm kiếm với từ khóa query='{}', lat={}, lng={}, radius={}", query, lat, lng, radius);

        List<PlaceResponse> places = placeProvider.searchVegetarianPlaces(query, lat, lng, radius, category);

        return ResponseEntity.ok(ApiResponse.<List<PlaceResponse>>builder()
                .success(true)
                .code(200)
                .message("Tìm thấy " + places.size() + " địa điểm phù hợp với từ khóa '" + query + "'")
                .data(places)
                .build());
    }

    @GetMapping("/categories")
    @Operation(
            summary = "Lấy danh mục phân loại địa điểm ăn uống & mua sắm chay",
            description = "Trả về thông tin và icon của các nhóm phân loại (Quán ăn, Siêu thị, Cửa hàng nguyên liệu) để hiển thị bộ lọc trên bản đồ."
    )
    public ResponseEntity<ApiResponse<List<CategoryMetadataDto>>> getCategories() {
        List<CategoryMetadataDto> categories = placeProvider.getCategoryMetadata();

        return ResponseEntity.ok(ApiResponse.<List<CategoryMetadataDto>>builder()
                .success(true)
                .code(200)
                .message("Lấy danh mục phân loại thành công")
                .data(categories)
                .build());
    }

    @GetMapping("/suggest-for-food")
    @Operation(
            summary = "Gợi ý quán chay và cửa hàng nguyên liệu phù hợp theo tên món ăn / thực phẩm",
            description = "Xếp hạng các quán chay, siêu thị và cửa hàng thực phẩm có thẻ vegetarian/vegan trên OpenStreetMap theo độ phù hợp với món ăn hoặc nguyên liệu tìm kiếm."
    )
    public ApiResponse<List<PlaceResponse>> suggestForFood(
            @RequestParam("food") String food,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radius", defaultValue = "5000") int radius
    ) {
        return ApiResponse.success(placeProvider.suggestShopsForFood(food, lat, lng, radius));
    }
}
