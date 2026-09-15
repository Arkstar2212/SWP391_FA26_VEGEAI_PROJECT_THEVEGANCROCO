package com.example.swp391_fa26_vegeai_project.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsDto {
    private long totalUsers;
    private long totalRecipes;
    private long aiQueriesToday;
    private long pendingModerationCount;
    private double systemHealthScore;
}
