package com.vegeai.backend.modules.fridge.entity;

/**
 * Trạng thái vòng đời của nguyên liệu trong tủ lạnh.
 */
public enum FridgeItemStatus {
    /**
     * Đang còn trong tủ lạnh (hoạt động).
     */
    ACTIVE,

    /**
     * Đã sử dụng hết để nấu ăn (tiêu thụ thành công).
     */
    CONSUMED,

    /**
     * Đã vứt bỏ do bị hỏng, ôi thiu hoặc quá hạn.
     */
    DISCARDED
}
