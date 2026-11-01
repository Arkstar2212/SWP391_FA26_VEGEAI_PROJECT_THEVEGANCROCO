package com.vegeai.backend.modules.market.controller;

import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.modules.market.dto.MarketCartDtos.CartResponse;
import com.vegeai.backend.modules.market.dto.MarketCartDtos.ReplaceCartRequest;
import com.vegeai.backend.modules.market.service.MarketCartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/cart")
@RequiredArgsConstructor
public class MarketCartController {
    private final MarketCartService service;

    @GetMapping
    public ApiResponse<CartResponse> get(@AuthenticationPrincipal String userId) {
        return ApiResponse.success(service.get(userId));
    }

    @PutMapping
    public ApiResponse<CartResponse> replace(@AuthenticationPrincipal String userId,
                                              @Valid @RequestBody ReplaceCartRequest request) {
        return ApiResponse.success(service.replace(userId, request.items()));
    }
}
