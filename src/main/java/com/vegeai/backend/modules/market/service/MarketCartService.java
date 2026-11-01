package com.vegeai.backend.modules.market.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.market.dto.MarketCartDtos.CartItem;
import com.vegeai.backend.modules.market.dto.MarketCartDtos.CartResponse;
import com.vegeai.backend.modules.market.entity.MarketCart;
import com.vegeai.backend.modules.market.repository.MarketCartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketCartService {
    private final MarketCartRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CartResponse get(String principal) {
        UUID userId = UUID.fromString(principal);
        return new CartResponse(repository.findById(userId).map(this::readItems).orElseGet(List::of));
    }

    @Transactional
    public CartResponse replace(String principal, List<CartItem> items) {
        UUID userId = UUID.fromString(principal);
        MarketCart cart = repository.findById(userId).orElseGet(() -> {
            MarketCart created = new MarketCart();
            created.setUserId(userId);
            return created;
        });
        try {
            cart.setItemsJson(objectMapper.writeValueAsString(items));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid cart payload.", exception);
        }
        repository.save(cart);
        return new CartResponse(items);
    }

    private List<CartItem> readItems(MarketCart cart) {
        try {
            return objectMapper.readValue(cart.getItemsJson(), new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }
}
