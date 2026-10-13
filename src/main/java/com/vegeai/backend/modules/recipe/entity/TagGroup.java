package com.vegeai.backend.modules.recipe.entity;

/**
 * Nhóm thẻ phân loại đa tiêu chí chuyên biệt cho ẩm thực chay (Standardized Vegetarian Taxonomy Groups).
 */
public enum TagGroup {
    /**
     * Trường phái ăn chay: Thuần chay (Vegan), Chay trứng sữa (Lacto-Ovo), Không ngũ vị tân, Chay thô...
     */
    DIETARY_TYPE,

    /**
     * Phương pháp chế biến: Món kho, Món xào, Món chiên, Món hấp/luộc...
     */
    COOKING_METHOD,

    /**
     * Loại món ăn: Món canh/Soup, Lẩu chay, Gỏi/Salad, Bún/Phở/Mì...
     */
    DISH_CATEGORY,

    /**
     * Mục tiêu dinh dưỡng: Giàu đạm, Giảm cân, Bổ máu/giàu sắt, Tốt tim mạch, Tiểu đường Low-GI, Dễ tiêu hóa...
     */
    NUTRITION_GOAL,

    /**
     * Thời gian và hoàn cảnh nấu: Nhanh < 15 phút, Dưới 30 phút, Nấu 1 nồi One-Pot, Tiết kiệm, Meal Prep...
     */
    OCCASION,

    /**
     * Dị ứng và thành phần cần loại trừ (Exclusion): Không đậu nành, Không gluten, Không hạt, Không nấm, Không cay...
     */
    DIETARY_RESTRICTION
}

