package com.vegeai.backend.modules.admin.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.modules.admin.dto.AiMonitoringDtos.*;
import com.vegeai.backend.modules.admin.service.AiMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin AI monitoring dashboard: KPIs, per-service health, AI event log, admin audit trail and server logs.
 */
@RestController
@RequestMapping("/api/admin/ai/monitoring")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "03. Super Admin & Governance", description = "Admin-only user governance, system telemetry, and AI Ops monitoring.")
@SecurityRequirement(name = "bearerAuth")
public class AdminAiMonitoringController {

    private final AiMonitoringService monitoringService;

    @GetMapping("/overview")
    @Operation(summary = "AI monitoring KPIs and per-service health", description = """
            **Tổng hợp số liệu AI trong 24 giờ gần nhất từ nhật ký đã ghi nhận.**

            - Lượt gọi (so với 24 giờ trước đó), độ trễ P95/trung bình, tỷ lệ thành công, tổng token.
            - Tỷ lệ AI gắn cờ bài viết, lượt gọi của khách hôm nay, trạng thái AI Core.
            - Tình trạng từng dịch vụ AI. Không kiểm tra kết nối trực tiếp tới nhà cung cấp.
            """)
    public ResponseEntity<ApiResponse<Overview>> overview() {
        return ResponseEntity.ok(ApiResponse.success(monitoringService.overview()));
    }

    @GetMapping("/events")
    @Operation(summary = "Filterable AI event log", description = """
            **Nhật ký lượt gọi AI có lọc và phân trang.** Có người gọi và số token; không có câu hỏi, ảnh hay phản hồi của AI.

            - `range`: TODAY, LAST_24_HOURS, LAST_7_DAYS, LAST_30_DAYS.
            - `level`: SUCCESS, FAILED, FLAGGED.
            - `search`: Request ID (`#REQ-1A2B3C4D` hoặc tiền tố UUID), tên người dùng, model hoặc mã lỗi.
            """)
    public ResponseEntity<ApiResponse<PageResponse<Event>>> events(
            @RequestParam(defaultValue = "LAST_24_HOURS") TimeRange range,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) EventLevel level,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                monitoringService.events(range, operation, level, search, page, size)));
    }

    @GetMapping("/events/export")
    @Operation(summary = "Export filtered AI events (max 5000 newest rows)")
    public ResponseEntity<ApiResponse<List<Event>>> exportEvents(
            @RequestParam(defaultValue = "LAST_24_HOURS") TimeRange range,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) EventLevel level,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(monitoringService.exportEvents(range, operation, level, search)));
    }

    @PostMapping("/events/{requestId}/lock")
    @Operation(summary = "Lock or unlock an AI event for investigation (toggle, recorded in the audit log)")
    public ResponseEntity<ApiResponse<LockResult>> toggleLock(@PathVariable UUID requestId,
                                                             @Valid @RequestBody(required = false) LockRequest request) {
        LockResult result = monitoringService.toggleLock(requestId, request == null ? null : request.reason());
        return ResponseEntity.ok(ApiResponse.success(result.locked() ? "Đã khóa request." : "Đã mở khóa request.", result));
    }

    @PostMapping("/events/{requestId}/replay")
    @Operation(summary = "Replay test: send a fixed probe prompt to the event's model", description = """
            Gửi một prompt kiểm tra cố định tới model của sự kiện để xác nhận đường gọi AI hoạt động.
            Nội dung gốc của người dùng không được lưu nên không thể gửi lại. Kết quả được ghi vào Audit Log.
            """)
    public ResponseEntity<ApiResponse<ReplayResult>> replay(@PathVariable UUID requestId) {
        return ResponseEntity.ok(ApiResponse.success(monitoringService.replay(requestId)));
    }

    @GetMapping("/audit")
    @Operation(summary = "Administrator audit trail: blog review decisions and AI monitoring actions")
    public ResponseEntity<ApiResponse<PageResponse<AuditEntry>>> audit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.success(monitoringService.auditEntries(page, size)));
    }

    @GetMapping("/system-logs")
    @Operation(summary = "Recent server logs kept in memory since the last restart (max 2000 lines)")
    public ResponseEntity<ApiResponse<PageResponse<SystemLogEntry>>> systemLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.success(monitoringService.systemLogs(level, search, page, size)));
    }
}
