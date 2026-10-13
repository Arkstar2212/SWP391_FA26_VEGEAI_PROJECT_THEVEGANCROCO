package com.vegeai.backend.modules.recipe.entity;

/**
 * Phân nhóm mức độ so khớp giữa công thức và kho tủ lạnh của người dùng:
 * - CAN_COOK_NOW: Khớp 100% nguyên liệu, có thể nấu ngay mà không cần mua thêm.
 * - MISSING_FEW: Khớp từ 60% đến 99%, chỉ cần bổ sung 1-2 nguyên liệu.
 * - INSUFFICIENT: Khớp dưới 60%, còn thiếu nhiều nguyên liệu.
 */
public enum MatchGroup {
    CAN_COOK_NOW,
    MISSING_FEW,
    INSUFFICIENT
}
