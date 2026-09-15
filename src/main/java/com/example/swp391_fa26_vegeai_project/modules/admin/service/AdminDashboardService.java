package com.example.swp391_fa26_vegeai_project.modules.admin.service;

import com.example.swp391_fa26_vegeai_project.modules.admin.dto.AdminStatsDto;
import com.example.swp391_fa26_vegeai_project.modules.ai.repository.AiAuditLogRepository;
import com.example.swp391_fa26_vegeai_project.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final AiAuditLogRepository aiAuditLogRepository;

    public AdminStatsDto getDashboardMetrics() {
        return AdminStatsDto.builder()
                .totalUsers(userRepository.count())
                .totalRecipes(124L) // Seeded / Mock aggregate
                .aiQueriesToday(aiAuditLogRepository.count())
                .pendingModerationCount(3L)
                .systemHealthScore(99.4)
                .build();
    }
}
