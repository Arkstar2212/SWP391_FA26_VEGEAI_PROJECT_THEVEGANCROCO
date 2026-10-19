package com.vegeai.backend.modules.place.util;

import java.util.Locale;

/**
 * Tiện ích tính toán khoảng cách địa lý (Haversine Formula).
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {
        // Utility class
    }

    /**
     * Tính khoảng cách đường chim bay giữa hai tọa độ địa lý theo công thức Haversine.
     *
     * @param lat1 Vĩ độ điểm 1
     * @param lon1 Kinh độ điểm 1
     * @param lat2 Vĩ độ điểm 2
     * @param lon2 Kinh độ điểm 2
     * @return Khoảng cách tính bằng km (làm tròn 2 chữ số thập phân)
     */
    public static double calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(rLat1) * Math.cos(rLat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS_KM * c;
        return Math.round(distance * 100.0) / 100.0;
    }

    /**
     * Định dạng khoảng cách hiển thị thân thiện (VD: "450 m" hoặc "1.8 km").
     */
    public static String formatDistanceText(double distanceKm) {
        if (distanceKm < 1.0) {
            int meters = (int) Math.round(distanceKm * 1000);
            return meters + " m";
        }
        return String.format(Locale.US, "%.1f km", distanceKm);
    }

}
