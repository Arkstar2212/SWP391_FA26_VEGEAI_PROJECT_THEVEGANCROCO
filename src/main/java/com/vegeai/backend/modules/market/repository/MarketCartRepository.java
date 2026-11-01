package com.vegeai.backend.modules.market.repository;

import com.vegeai.backend.modules.market.entity.MarketCart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MarketCartRepository extends JpaRepository<MarketCart, UUID> {
}
