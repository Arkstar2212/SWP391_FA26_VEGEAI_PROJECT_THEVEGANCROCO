package com.vegeai.backend.modules.fridge.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.fridge.dto.*;
import com.vegeai.backend.modules.fridge.entity.FridgeItemStatus;
import com.vegeai.backend.modules.fridge.service.FridgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fridge")
@RequiredArgsConstructor
@Tag(name = "05. Smart Fridge Inventory", description = "Quản lý kho tủ lạnh thông minh, lưu vết hạn sử dụng, cơ chế Smart Memory qua các lần quét AI và cảnh báo sắp hỏng.")
@SecurityRequirement(name = "bearerAuth")
public class FridgeController {

    private final FridgeService fridgeService;

    @PostMapping("/confirm-items")
    @Operation(
            summary = "Xác nhận & lưu nguyên liệu sau khi quét AI (User-in-the-Loop)",
            description = """
                    **Cơ chế lưu nhớ Smart Fridge Memory & User-in-the-Loop:**
                    - Người dùng sau khi chụp ảnh và nhận diện nguyên liệu, có thể chỉnh sửa/xóa bớt/thêm nhãn trên giao diện rồi bấm **Lưu tủ lạnh**.
                    - Backend tự động so khớp với kho tủ lạnh hiện tại của user:
                      - **Nếu là nguyên liệu cũ (đã có trong tủ):** Cập nhật lần quét mới nhất (`lastScannedAt`), tính số ngày đã nằm trong tủ (`daysInFridge`), đếm lùi hạn dùng và tự động cảnh báo nếu sắp hỏng.
                      - **Nếu là nguyên liệu mới:** Lưu vào kho với thời điểm bắt đầu (`firstScannedAt`) và ngày hết hạn dự kiến.
                    """
    )
    public ResponseEntity<ApiResponse<ConfirmFridgeScanResponse>> confirmScanItems(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody ConfirmFridgeScanRequest request) {
        ConfirmFridgeScanResponse response = fridgeService.confirmScanItems(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/items")
    @Operation(
            summary = "Thêm thủ công nguyên liệu vào tủ lạnh",
            description = "Cho phép người dùng tự gõ thêm một nguyên liệu vào tủ lạnh mà không cần quét ảnh."
    )
    public ResponseEntity<ApiResponse<FridgeItemResponse>> addManualItem(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateFridgeItemRequest request) {
        FridgeItemResponse response = fridgeService.addManualItem(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/items")
    @Operation(
            summary = "Lấy danh sách nguyên liệu trong tủ lạnh",
            description = """
                    **Xem toàn bộ kho tủ lạnh của tài khoản đang đăng nhập.**
                    - Mặc định trả về các món đang còn trong tủ (`status = ACTIVE`), sắp xếp theo hạn sử dụng tăng dần (món sắp hỏng lên trên đầu).
                    - Có thể lọc theo trạng thái: `ACTIVE`, `CONSUMED` (đã ăn), `DISCARDED` (đã vứt).
                    """
    )
    public ResponseEntity<ApiResponse<List<FridgeItemResponse>>> getMyFridgeItems(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "Trạng thái nguyên liệu: ACTIVE, CONSUMED, DISCARDED (Mặc định: ACTIVE)")
            @RequestParam(required = false) FridgeItemStatus status) {
        List<FridgeItemResponse> items = fridgeService.getMyFridgeItems(userId, status);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @GetMapping("/items/{id}")
    @Operation(
            summary = "Xem chi tiết một nguyên liệu trong tủ",
            description = "Trả về đầy đủ thông tin: số ngày đã nằm trong tủ, hạn dùng còn lại, mẹo bảo quản và cảnh báo."
    )
    public ResponseEntity<ApiResponse<FridgeItemResponse>> getFridgeItemById(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {
        FridgeItemResponse item = fridgeService.getFridgeItemById(userId, id);
        return ResponseEntity.ok(ApiResponse.success(item));
    }

    @PutMapping("/items/{id}")
    @Operation(
            summary = "Chỉnh sửa thông tin nguyên liệu",
            description = "Cho phép cập nhật tên, số lượng, đơn vị, trạng thái tươi mới, ngày hết hạn, mẹo bảo quản hoặc ghi chú."
    )
    public ResponseEntity<ApiResponse<FridgeItemResponse>> updateItem(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFridgeItemRequest request) {
        FridgeItemResponse updated = fridgeService.updateItem(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PatchMapping("/items/{id}/status")
    @Operation(
            summary = "Cập nhật trạng thái nguyên liệu (Đã ăn / Đã vứt)",
            description = """
                    **Thay đổi vòng đời của món ăn trong tủ:**
                    - `CONSUMED`: Người dùng đã đem nấu ăn xong.
                    - `DISCARDED`: Nguyên liệu bị hỏng/ôi thiu và người dùng đã vứt bỏ.
                    - `ACTIVE`: Chuyển lại vào tủ lạnh đang dùng.
                    """
    )
    public ResponseEntity<ApiResponse<FridgeItemResponse>> updateItemStatus(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFridgeItemStatusRequest request) {
        FridgeItemResponse updated = fridgeService.updateItemStatus(userId, id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/items/{id}")
    @Operation(
            summary = "Xóa nguyên liệu khỏi tủ lạnh",
            description = "Xóa hoàn toàn nguyên liệu này ra khỏi cơ sở dữ liệu."
    )
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {
        fridgeService.deleteItem(userId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/expiring-soon")
    @Operation(
            summary = "Lấy danh sách thực phẩm sắp hết hạn (Cảnh báo chống lãng phí)",
            description = """
                    **Lấy danh sách các món cần ưu tiên nấu ngay.**
                    - Trả về các món có hạn sử dụng còn $\\le 2$ ngày (hoặc số ngày tùy chọn) hoặc đã quá hạn.
                    - Frontend dùng danh sách này để hiển thị banner cảnh báo và nút **'Gợi ý công thức cứu nguy thực phẩm'**.
                    """
    )
    public ResponseEntity<ApiResponse<List<FridgeItemResponse>>> getExpiringSoonItems(
            @AuthenticationPrincipal String userId,
            @Parameter(description = "Số ngày ngưỡng cảnh báo (mặc định: 2 ngày)")
            @RequestParam(defaultValue = "2") int days) {
        List<FridgeItemResponse> items = fridgeService.getExpiringSoonItems(userId, days);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Thống kê tổng quan kho tủ lạnh",
            description = "Báo cáo số lượng món đang có, số món tươi, số món cần dùng gấp, số món sắp hết hạn và các cảnh báo khẩn cấp."
    )
    public ResponseEntity<ApiResponse<FridgeSummaryResponse>> getFridgeSummary(
            @AuthenticationPrincipal String userId) {
        FridgeSummaryResponse summary = fridgeService.getFridgeSummary(userId);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }
}
