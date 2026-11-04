package com.vegeai.backend.modules.nutrition.service;

import com.vegeai.backend.modules.nutrition.dto.NutritionTargetsResponse;
import com.vegeai.backend.modules.user.entity.User;

/** Published after a user's nutrition targets are recalculated, so dependent data (e.g. saved meal plans) can follow. */
public record NutritionTargetsChangedEvent(User user, NutritionTargetsResponse targets) {}
