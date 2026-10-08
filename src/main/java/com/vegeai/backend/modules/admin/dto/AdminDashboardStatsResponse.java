package com.vegeai.backend.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminDashboardStatsResponse {

    private final long totalUsers;
    private final long activeUsers;
    private final long pendingUsers;
    private final long lockedUsers;
    private final long disabledUsers;
    private final long memberCount;
    private final long adminCount;
    private final long newUsersToday;
    private final long newUsersThisWeek;
}
