package com.example.swp391_fa26_vegeai_project.modules.ai.repository;

import com.example.swp391_fa26_vegeai_project.modules.ai.entity.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, Long> {
    List<AiAuditLog> findByUserId(Long userId);
    List<AiAuditLog> findTop50ByOrderByCreatedAtDesc();
}
