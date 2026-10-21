package com.vegeai.backend.modules.interactions.repository;

import java.util.UUID;

public interface RatingAggregate {
    UUID getTargetId();
    Double getAverageStars();
    long getRatingCount();
}
