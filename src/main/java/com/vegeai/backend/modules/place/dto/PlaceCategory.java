package com.vegeai.backend.modules.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Phân loại địa điểm ẩm thực và cửa hàng chay.
 */
@Schema(description = "Phân loại địa điểm (ALL: Tất cả, RESTAURANT: Quán ăn/Nhà hàng chay, SUPERMARKET: Siêu thị chay, GROCERY_STORE: Cửa hàng nguyên liệu, DIEN_MAY_XANH: Điện Máy Xanh & Thiết bị bếp)")
public enum PlaceCategory {
    ALL,
    RESTAURANT,
    SUPERMARKET,
    GROCERY_STORE,
    DIEN_MAY_XANH
}
