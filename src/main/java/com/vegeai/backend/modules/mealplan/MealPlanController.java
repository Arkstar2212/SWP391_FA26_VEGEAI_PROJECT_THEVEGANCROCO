package com.vegeai.backend.modules.mealplan;
import com.vegeai.backend.common.dto.ApiResponse;
import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.modules.recipe.dto.RecipeSummaryDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
import static com.vegeai.backend.modules.mealplan.MealPlanDtos.*;
@RestController @RequestMapping("/api/meal-plans") @RequiredArgsConstructor @PreAuthorize("hasRole('MEMBER')")
public class MealPlanController {
    private final MealPlanService service;
    private final SeasonalMealService seasonalMealService;
    @PostMapping("/generate") public ApiResponse<View> generate(@AuthenticationPrincipal String user,@Valid @RequestBody Generate body) { return ApiResponse.success(service.generate(user,body)); }
    @GetMapping public ApiResponse<PageResponse<Summary>> list(@AuthenticationPrincipal String user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="5") int size) { return ApiResponse.success(service.list(user,page,size)); }
    @GetMapping("/calendar") public ApiResponse<List<CalendarDay>> calendar(@AuthenticationPrincipal String user,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) { return ApiResponse.success(service.calendar(user,from,to)); }
    @GetMapping("/{id}") public ApiResponse<View> get(@AuthenticationPrincipal String user,@PathVariable UUID id) { return ApiResponse.success(service.get(user,id)); }
    @GetMapping("/{id}/options") public ApiResponse<List<RecipeSummaryDto>> options(@AuthenticationPrincipal String user,@PathVariable UUID id) { return ApiResponse.success(service.options(user,id)); }
    @PutMapping("/{id}/days/{day}/meals/{slot}") public ApiResponse<View> swap(@AuthenticationPrincipal String user,@PathVariable UUID id,@PathVariable int day,@PathVariable int slot,@Valid @RequestBody Swap body) { return ApiResponse.success(service.swap(user,id,day,slot,body.recipeId())); }
    @PostMapping("/{id}/save") public ApiResponse<View> save(@AuthenticationPrincipal String user,@PathVariable UUID id) { return ApiResponse.success(service.save(user,id)); }
    @GetMapping("/seasonal-regional") public ApiResponse<SeasonalMealDtos.SeasonalRegionalResponse> seasonalRegional(@AuthenticationPrincipal String user,@RequestParam(required=false) SeasonalMealDtos.Season season,@RequestParam(required=false) SeasonalMealDtos.Region region,@RequestParam(defaultValue="false") boolean refresh) { return ApiResponse.success(seasonalMealService.getRecommendations(user,season,region,refresh)); }
}
