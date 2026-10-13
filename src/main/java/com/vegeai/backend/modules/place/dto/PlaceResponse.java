package com.vegeai.backend.modules.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Phản hồi chi tiết thông tin địa điểm quán ăn hoặc cửa hàng chay (Google Places API).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết quán ăn, siêu thị hoặc cửa hàng thực phẩm chay")
public class PlaceResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Mã Google Place ID hoặc định danh hệ thống", example = "ChIJN1t_tDeuEmsRUsoyG83frY4")
    private String placeId;

    @Schema(description = "Tên quán ăn hoặc cửa hàng chay", example = "Cơm Chay Thiện Tâm")
    private String name;

    @Schema(description = "Phân loại địa điểm (RESTAURANT, SUPERMARKET, GROCERY_STORE)", example = "RESTAURANT")
    private PlaceCategory category;

    @Schema(description = "Địa chỉ chi tiết đầy đủ", example = "123 Lê Văn Việt, Tăng Nhơn Phú A, TP. Thủ Đức, TP. Hồ Chí Minh")
    private String address;

    @Schema(description = "Vĩ độ (Latitude)", example = "10.8456")
    private Double latitude;

    @Schema(description = "Kinh độ (Longitude)", example = "106.7924")
    private Double longitude;

    @Schema(description = "Khoảng cách từ vị trí người dùng (km)", example = "0.85")
    private Double distanceKm;

    @Schema(description = "Khoảng cách hiển thị thân thiện", example = "850 m")
    private String distanceText;

    @Schema(description = "Điểm đánh giá Google (thang 5.0)", example = "4.8")
    private Double rating;

    @Schema(description = "Tổng số lượt đánh giá từ khách hàng", example = "186")
    private Integer userRatingsTotal;

    @Schema(description = "Trạng thái đang mở cửa hay đóng cửa", example = "true")
    private Boolean isOpenNow;

    @Schema(description = "URL hình ảnh đại diện", example = "https://images.unsplash.com/photo-1512621776951-a57141f2eefd")
    private String photoUrl;

    @Schema(description = "URL chỉ đường trực tiếp trên Google Maps (Click để mở App Maps)", example = "https://www.google.com/maps/dir/?api=1&destination=10.8456,106.7924")
    private String googleMapsUrl;

    @Schema(description = "Số điện thoại liên hệ", example = "028 3730 1234")
    private String phoneNumber;

    @Schema(description = "Danh sách thẻ loại hình từ Google Places", example = "[\"vegetarian_restaurant\", \"food\", \"point_of_interest\"]")
    private List<String> types;

    @Schema(description = "Đã được xác minh thuần chay 100% bởi VEGEAI", example = "true")
    private boolean isVerified;
}
