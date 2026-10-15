package com.vegeai.backend.modules.recipe.repository;

import com.vegeai.backend.modules.recipe.entity.CustomRecipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomRecipeRepository extends JpaRepository<CustomRecipe, UUID> {

    /**
     * Lấy toàn bộ danh sách công thức trong sổ tay của người dùng theo thời gian tạo mới nhất.
     */
    Page<CustomRecipe> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Tìm kiếm công thức trong sổ tay người dùng theo từ khóa (tên món hoặc mô tả).
     */
    @Query("SELECT c FROM CustomRecipe c WHERE c.userId = :userId AND " +
           "(LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           " LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY c.createdAt DESC")
    Page<CustomRecipe> searchByUserIdAndKeyword(@Param("userId") UUID userId,
                                                @Param("keyword") String keyword,
                                                Pageable pageable);

    /**
     * Tìm chi tiết công thức thuộc quyền sở hữu của người dùng.
     */
    Optional<CustomRecipe> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Kiểm tra công thức có tồn tại và thuộc người dùng không.
     */
    boolean existsByIdAndUserId(UUID id, UUID userId);

    /**
     * Xóa công thức thuộc sở hữu của người dùng.
     */
    void deleteByIdAndUserId(UUID id, UUID userId);

    /**
     * Đếm tổng số món ăn trong sổ tay của người dùng.
     */
    long countByUserId(UUID userId);
}
