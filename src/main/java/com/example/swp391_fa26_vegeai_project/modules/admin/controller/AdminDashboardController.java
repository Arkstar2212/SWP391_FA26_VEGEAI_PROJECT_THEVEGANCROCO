package com.example.swp391_fa26_vegeai_project.modules.admin.controller;

import com.example.swp391_fa26_vegeai_project.common.dto.ApiResponse;
import com.example.swp391_fa26_vegeai_project.modules.admin.dto.AdminStatsDto;
import com.example.swp391_fa26_vegeai_project.modules.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard/stats")
    public ResponseEntity<ApiResponse<AdminStatsDto>> getStats() {
        AdminStatsDto stats = adminDashboardService.getDashboardMetrics();
        return ResponseEntity.ok(ApiResponse.ok("Admin stats retrieved", stats));
    }
}
