package com.vegeai.backend.modules.market.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class MarketCartDtos {
    private MarketCartDtos() {}

    public record CartItem(
            @NotBlank @Size(max = 240) String id,
            @NotBlank @Size(max = 160) String catalogItemId,
            @NotBlank @Size(max = 200) String name,
            @Min(0) long price,
            @Size(max = 50) String priceFormatted,
            @NotBlank @Size(max = 60) String unit,
            @Size(max = 20) String imageEmoji,
            @NotBlank @Size(max = 160) String storeId,
            @NotBlank @Size(max = 200) String storeName,
            @Size(max = 500) String storeAddress,
            @Size(max = 100) String storeCategory,
            @Min(1) @Max(99) int count) {}

    public record ReplaceCartRequest(@NotNull @Size(max = 200) List<@Valid CartItem> items) {}
    public record CartResponse(List<CartItem> items) {}
}
