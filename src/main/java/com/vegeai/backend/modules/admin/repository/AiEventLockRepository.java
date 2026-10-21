package com.vegeai.backend.modules.admin.repository;

import com.vegeai.backend.modules.admin.entity.AiEventLock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiEventLockRepository extends JpaRepository<AiEventLock, UUID> {
}
