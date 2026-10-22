package com.vegeai.backend.modules.community.repository;

import com.vegeai.backend.modules.community.entity.BlogComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BlogCommentRepository extends JpaRepository<BlogComment, UUID> {
    List<BlogComment> findByPostIdOrderByCreatedAtDesc(UUID postId);
    Page<BlogComment> findByPostIdOrderByCreatedAtDesc(UUID postId, Pageable pageable);
    long countByPostId(UUID postId);
}
