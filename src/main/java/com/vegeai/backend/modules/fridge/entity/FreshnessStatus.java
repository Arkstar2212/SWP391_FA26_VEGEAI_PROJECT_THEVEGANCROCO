package com.vegeai.backend.modules.fridge.entity;

/**
 * Trạng thái độ tươi của thực phẩm trong tủ lạnh.
 */
public enum FreshnessStatus {
    /**
     * Tươi mới, chất lượng tốt, bảo quản an toàn.
     */
    FRESH,

    /**
     * Cần dùng ngay (sắp héo, chín già, còn 1-2 ngày hạn dùng).
     */
    USABLE,

    /**
     * Đã hỏng, nấm mốc hoặc biến chất, không an toàn để sử dụng.
     */
    SPOILED
}
