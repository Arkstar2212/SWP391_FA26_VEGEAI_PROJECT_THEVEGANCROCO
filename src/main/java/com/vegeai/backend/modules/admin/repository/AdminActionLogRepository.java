package com.vegeai.backend.modules.admin.repository;

import com.vegeai.backend.modules.admin.entity.AdminActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, UUID> {
}
