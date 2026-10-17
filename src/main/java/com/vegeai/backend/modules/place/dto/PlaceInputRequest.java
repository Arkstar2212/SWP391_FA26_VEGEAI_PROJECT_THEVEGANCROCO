package com.vegeai.backend.modules.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dữ liệu thêm mới hoặc cập nhật địa điểm trên bản đồ bởi Admin")
public class PlaceInputRequest {

    @NotBlank(message = "Tên địa điểm không được để trống")
    @Size(max = 200)
    @Schema(description = "Tên quán chay, siêu thị hoặc cửa hàng Điện Máy Xanh", example = "Cơm Chay Hoa Sen")
    private String name;

    @NotNull(message = "Phân loại địa điểm không được để trống")
    @Schema(description = "Phân loại (RESTAURANT, SUPERMARKET, GROCERY_STORE, DIEN_MAY_XANH)", example = "RESTAURANT")
    private PlaceCategory category;

    @NotBlank(message = "Địa chỉ không được để trống")
    @Size(max = 400)
    @Schema(description = "Địa chỉ chi tiết", example = "120 Nguyễn Thị Minh Khai, Quận 1, TP.HCM")
    private String address;

    @NotNull(message = "Vĩ độ (latitude) không được để trống")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @Schema(description = "Vĩ độ", example = "10.7769")
    private Double latitude;

    @NotNull(message = "Kinh độ (longitude) không được để trống")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @Schema(description = "Kinh độ", example = "106.7009")
    private Double longitude;

    @DecimalMin(value = "1.0")
    @DecimalMax(value = "5.0")
    @Schema(description = "Điểm đánh giá (1.0 - 5.0)", example = "4.8")
    private Double rating;

    @PositiveOrZero
    @Schema(description = "Số lượt đánh giá", example = "150")
    private Integer userRatingsTotal;

    @Schema(description = "Đang mở cửa", example = "true")
    private Boolean isOpenNow;

    @Size(max = 1000)
    @Schema(description = "URL hình ảnh", example = "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=500")
    private String photoUrl;

    @Size(max = 60)
    @Schema(description = "Số điện thoại", example = "028 3822 8899")
    private String phoneNumber;

    @Schema(description = "Đã xác minh bởi VEGEAI", example = "true")
    private Boolean isVerified;
}
