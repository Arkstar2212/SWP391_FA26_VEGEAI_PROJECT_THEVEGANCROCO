package com.vegeai.backend.modules.place.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.place.dto.CategoryMetadataDto;
import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.dto.PlaceResponse;
import com.vegeai.backend.modules.place.service.GooglePlacesService;
import com.vegeai.backend.modules.place.service.VerifiedPlacesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "10. Google Maps & Vegetarian Places", description = "Tìm kiếm đề xuất quán ăn chay, siêu thị chay và cửa hàng thực phẩm chay gần nhất với Google Maps")
public class PlaceController {

    private final GooglePlacesService googlePlacesService;
    private final VerifiedPlacesService verifiedPlacesService;

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
            summary = "Tìm kiếm quán ăn, siêu thị và cửa hàng chay quanh tọa độ hiện tại",
            description = "Trả về danh sách địa điểm chay trong bán kính (mặc định 5km), sắp xếp từ gần đến xa theo công thức Haversine."
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

        List<PlaceResponse> places = googlePlacesService.findNearbyVegetarianPlaces(lat, lng, radius, category, keyword);

        return ResponseEntity.ok(ApiResponse.<List<PlaceResponse>>builder()
                .success(true)
                .code(200)
                .message("Tìm thấy " + places.size() + " địa điểm chay quanh bạn")
                .data(places)
                .build());
    }

    @GetMapping("/search")
    @Operation(
            summary = "Tìm kiếm địa điểm chay theo từ khóa hoặc tên đường",
            description = "Hỗ trợ tìm kiếm theo tên quán, địa danh hoặc tên đường gần vị trí người dùng."
    )
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> searchVegetarianPlaces(
            @Parameter(description = "Từ khóa tìm kiếm (VD: 'Cơm chay Quận 1', 'Đại An')", example = "Cơm chay Quận 1", required = true)
            @RequestParam("query") String query,

            @Parameter(description = "Vĩ độ người dùng (tùy chọn)", example = "10.7769")
            @RequestParam(value = "lat", required = false) Double lat,

            @Parameter(description = "Kinh độ người dùng (tùy chọn)", example = "106.7009")
            @RequestParam(value = "lng", required = false) Double lng,

            @Parameter(description = "Bán kính tìm kiếm tính bằng mét (mặc định 10000 = 10km)", example = "10000")
            @RequestParam(value = "radius", defaultValue = "10000") int radius
    ) {
        log.info("[PLACES] Tìm kiếm với từ khóa query='{}', lat={}, lng={}, radius={}", query, lat, lng, radius);

        List<PlaceResponse> places = googlePlacesService.searchVegetarianPlaces(query, lat, lng, radius);

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
        List<CategoryMetadataDto> categories = googlePlacesService.getCategoryMetadata();

        return ResponseEntity.ok(ApiResponse.<List<CategoryMetadataDto>>builder()
                .success(true)
                .code(200)
                .message("Lấy danh mục phân loại thành công")
                .data(categories)
                .build());
    }
}
