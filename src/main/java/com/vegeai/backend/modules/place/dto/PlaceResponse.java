package com.vegeai.backend.modules.place.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Place details sourced from OpenStreetMap community-contributed tags.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết quán ăn, siêu thị hoặc cửa hàng thực phẩm chay")
public class PlaceResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "OpenStreetMap element ID dạng type/id", example = "node/123456")
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

    @Schema(description = "OpenStreetMap không cung cấp rating; giá trị thường là null")
    private Double rating;

    @Schema(description = "OpenStreetMap không cung cấp số lượt đánh giá; giá trị thường là null")
    private Integer userRatingsTotal;

    @Schema(description = "Chỉ có giá trị nếu nguồn OSM cung cấp đủ dữ liệu để xác định; thông thường là null")
    private Boolean isOpenNow;

    @Schema(description = "URL ảnh nếu địa điểm có thẻ image hợp lệ; có thể null")
    private String photoUrl;

    @Schema(description = "URL mở địa điểm trên OpenStreetMap", example = "https://www.openstreetmap.org/node/123456")
    private String openStreetMapUrl;

    @Schema(description = "Original OpenStreetMap object URL")
    private String openStreetMapUrl;

    @Schema(description = "Data source attribution")
    private String attribution;

    @Schema(description = "Số điện thoại liên hệ", example = "028 3730 1234")
    private String phoneNumber;

    @Schema(description = "Danh sách thẻ OSM dạng key=value", example = "[\"amenity=restaurant\", \"cuisine=vegetarian\"]")
    private List<String> types;

    @Schema(description = "Có thẻ OSM thể hiện vegetarian/vegan-only hoặc cuisine vegetarian/vegan; không phải xác minh độc lập của VEGEAI", example = "true")
    @JsonProperty("isVerified")
    private boolean isVerified;

<<<<<<< HEAD
    // Preserve legacy "verified" while exposing the field consumed by the current FE.
    @com.fasterxml.jackson.annotation.JsonProperty("isVerified")
    public boolean getIsVerified() { return isVerified; }
=======
    @Schema(description = "Nguồn của tín hiệu vegetarian-only, nếu có", example = "OPENSTREETMAP_TAGS")
    private String verificationSource;

    @Schema(description = "Nguồn dữ liệu địa điểm", example = "OPENSTREETMAP")
    private String dataSource;

    @Schema(description = "Yêu cầu hiển thị credit OpenStreetMap", example = "© OpenStreetMap contributors")
    private String attribution;
>>>>>>> 6a63815723b213a298a5c7dd70a9dbc7665793ab
}
