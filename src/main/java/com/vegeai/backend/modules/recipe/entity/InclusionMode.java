package com.vegeai.backend.modules.recipe.entity;

/**
 * Chế độ bao gồm thẻ lọc (Inclusion Mode):
 * - AND: Phải thỏa mãn TẤT CẢ các thẻ đã chọn.
 * - OR: Thỏa mãn BẤT KỲ thẻ nào trong số các thẻ đã chọn.
 */
public enum InclusionMode {
    AND,
    OR
}
