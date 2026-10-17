package com.vegeai.backend.modules.place.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.place.dto.CategoryMetadataDto;
import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.dto.PlaceResponse;
import com.vegeai.backend.modules.place.service.GooglePlacesService;
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
@Tag(name = "10. Google Maps & Vegetarian Places", description = "API Bản đồ địa điểm quán chay, siêu thị chay, cửa hàng nguyên liệu và Điện Máy Xanh")
public class PlaceController {

    private final GooglePlacesService googlePlacesService;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách tất cả địa điểm trên bản đồ (Quán chay, Siêu thị, Điện Máy Xanh & địa điểm Admin thêm)",
            description = "Hiển thị trực tiếp các địa điểm đã gắn trên bản đồ API kèm tính khoảng cách tới người dùng."
    )
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> getAllMapPlaces(
            @Parameter(description = "Phân loại địa điểm (ALL, RESTAURANT, SUPERMARKET, GROCERY_STORE, DIEN_MAY_XANH)", example = "ALL")
            @RequestParam(value = "category", defaultValue = "ALL") PlaceCategory category,

            @Parameter(description = "Từ khóa tìm kiếm", example = "Điện Máy Xanh")
            @RequestParam(value = "keyword", required = false) String keyword,

            @Parameter(description = "Vĩ độ tham chiếu (tùy chọn)", example = "10.7769")
            @RequestParam(value = "lat", required = false) Double lat,

            @Parameter(description = "Kinh độ tham chiếu (tùy chọn)", example = "106.7009")
            @RequestParam(value = "lng", required = false) Double lng
    ) {
        List<PlaceResponse> places = googlePlacesService.getAllMapPlaces(category, keyword, lat, lng);
        return ResponseEntity.ok(ApiResponse.<List<PlaceResponse>>builder()
                .success(true)
                .code(200)
                .message("Tải " + places.size() + " địa điểm trên bản đồ thành công")
                .data(places)
                .build());
    }

    @GetMapping("/nearby")
    @Operation(
            summary = "Tìm kiếm quán ăn, siêu thị, cửa hàng chay và Điện Máy Xanh quanh tọa độ hiện tại",
            description = "Trả về danh sách địa điểm trong bán kính (mặc định 5km), sắp xếp từ gần đến xa theo công thức Haversine."
    )
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> getNearbyVegetarianPlaces(
            @Parameter(description = "Vĩ độ của người dùng (Latitude)", example = "10.7769", required = true)
            @RequestParam("lat") double lat,

            @Parameter(description = "Kinh độ của người dùng (Longitude)", example = "106.7009", required = true)
            @RequestParam("lng") double lng,

            @Parameter(description = "Bán kính tìm kiếm tính bằng mét (mặc định 5000 = 5km)", example = "5000")
            @RequestParam(value = "radius", defaultValue = "5000") int radius,

            @Parameter(description = "Phân loại địa điểm (ALL, RESTAURANT, SUPERMARKET, GROCERY_STORE, DIEN_MAY_XANH)", example = "ALL")
            @RequestParam(value = "category", defaultValue = "ALL") PlaceCategory category,

            @Parameter(description = "Từ khóa tìm kiếm bổ sung (ví dụ: 'buffet', 'Điện Máy Xanh', 'đậu phụ')", example = "buffet")
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        log.info("[PLACES] Yêu cầu tìm kiếm quanh lat={}, lng={}, radius={}m, category={}, keyword='{}'",
                lat, lng, radius, category, keyword);

        List<PlaceResponse> places = googlePlacesService.findNearbyVegetarianPlaces(lat, lng, radius, category, keyword);

        return ResponseEntity.ok(ApiResponse.<List<PlaceResponse>>builder()
                .success(true)
                .code(200)
                .message("Tìm thấy " + places.size() + " địa điểm quanh bạn")
                .data(places)
                .build());
    }

    @GetMapping("/search")
    @Operation(
            summary = "Tìm kiếm địa điểm chay hoặc Điện Máy Xanh theo từ khóa hoặc tên đường",
            description = "Hỗ trợ tìm kiếm theo tên quán, siêu thị Điện Máy Xanh, địa danh hoặc tên đường gần vị trí người dùng."
    )
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> searchVegetarianPlaces(
            @Parameter(description = "Từ khóa tìm kiếm (VD: 'Cơm chay Quận 1', 'Điện Máy Xanh')", example = "Cơm chay Quận 1", required = true)
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
            summary = "Lấy danh mục phân loại địa điểm ăn uống, mua sắm chay & Điện Máy Xanh",
            description = "Trả về thông tin và icon của các nhóm phân loại để hiển thị bộ lọc trên bản đồ."
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
