package com.vegeai.backend.modules.place.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.dto.PlaceInputRequest;
import com.vegeai.backend.modules.place.dto.PlaceResponse;
import com.vegeai.backend.modules.place.service.GooglePlacesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/places")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
@Tag(name = "10B. Admin Map Places Management", description = "Quản trị viên thêm thủ công, chỉnh sửa hoặc xóa các quán chay, siêu thị và Điện Máy Xanh trên bản đồ")
public class AdminPlaceController {

    private final GooglePlacesService googlePlacesService;

    @GetMapping
    @Operation(summary = "Danh sách toàn bộ địa điểm trên bản đồ dành cho Admin")
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> listPlaces(
            @RequestParam(value = "category", defaultValue = "ALL") PlaceCategory category,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        List<PlaceResponse> places = googlePlacesService.getAllMapPlaces(category, keyword, 10.7769, 106.7009);
        return ResponseEntity.ok(ApiResponse.success("Tải danh sách địa điểm thành công", places));
    }

    @PostMapping
    @Operation(summary = "Admin thêm thủ công một quán chay, siêu thị hoặc cửa hàng Điện Máy Xanh lên bản đồ")
    public ResponseEntity<ApiResponse<PlaceResponse>> createPlace(@Valid @RequestBody PlaceInputRequest request) {
        log.info("[ADMIN-PLACES] Thêm mới địa điểm '{}' ({}) tại '{}'", request.getName(), request.getCategory(), request.getAddress());
        PlaceResponse created = googlePlacesService.createPlace(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PlaceResponse>builder()
                        .success(true)
                        .code(201)
                        .message("Đã thêm địa điểm lên bản đồ thành công")
                        .data(created)
                        .build());
    }

    @PutMapping("/{placeId}")
    @Operation(summary = "Admin cập nhật thông tin địa điểm trên bản đồ")
    public ResponseEntity<ApiResponse<PlaceResponse>> updatePlace(
            @PathVariable("placeId") String placeId,
            @Valid @RequestBody PlaceInputRequest request
    ) {
        log.info("[ADMIN-PLACES] Cập nhật địa điểm placeId='{}'", placeId);
        PlaceResponse updated = googlePlacesService.updatePlace(placeId, request);
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật địa điểm thành công", updated));
    }

    @DeleteMapping("/{placeId}")
    @Operation(summary = "Admin xóa địa điểm khỏi bản đồ")
    public ResponseEntity<ApiResponse<Void>> deletePlace(@PathVariable("placeId") String placeId) {
        log.info("[ADMIN-PLACES] Xóa địa điểm placeId='{}'", placeId);
        googlePlacesService.deletePlace(placeId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa địa điểm khỏi bản đồ", null));
    }
}
