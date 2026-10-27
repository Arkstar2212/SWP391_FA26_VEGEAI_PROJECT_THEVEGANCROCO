package com.vegeai.backend.modules.place.service;

import com.vegeai.backend.modules.place.dto.CategoryMetadataDto;
import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.dto.PlaceResponse;

import java.util.List;

public interface PlaceProvider {

    List<PlaceResponse> findNearbyVegetarianPlaces(
            double latitude,
            double longitude,
            int radiusMeters,
            PlaceCategory category,
            String keyword);

    List<PlaceResponse> searchVegetarianPlaces(
            String query,
            Double latitude,
            Double longitude,
            int radiusMeters,
            PlaceCategory category);

    List<CategoryMetadataDto> getCategoryMetadata();
}
