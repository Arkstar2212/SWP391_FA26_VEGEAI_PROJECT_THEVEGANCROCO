package com.vegeai.backend.modules.place.dto;

/** A real OpenStreetMap location that can be selected before loading nearby places. */
public record AreaSuggestion(String name, String address, double lat, double lon, String category) {
}
