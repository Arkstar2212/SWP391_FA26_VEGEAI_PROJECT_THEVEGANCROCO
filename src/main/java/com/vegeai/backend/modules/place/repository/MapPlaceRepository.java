package com.vegeai.backend.modules.place.repository;

import com.vegeai.backend.modules.place.dto.PlaceCategory;
import com.vegeai.backend.modules.place.entity.MapPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MapPlaceRepository extends JpaRepository<MapPlace, UUID> {
    Optional<MapPlace> findByPlaceId(String placeId);
    List<MapPlace> findByCategory(PlaceCategory category);
    boolean existsByPlaceId(String placeId);
}
