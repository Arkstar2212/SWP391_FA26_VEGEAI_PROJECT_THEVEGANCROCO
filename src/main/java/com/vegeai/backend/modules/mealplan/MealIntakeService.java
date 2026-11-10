package com.vegeai.backend.modules.mealplan;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.community.service.CommunityAccess;
import com.vegeai.backend.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.vegeai.backend.modules.mealplan.MealPlanDtos.*;

@Service @RequiredArgsConstructor
public class MealIntakeService {
    private final MealIntakeRepository intakes;
    private final MealPlanService plans;
    private final CommunityAccess access;

    public record Intake(UUID id, LocalDate date, int slot, UUID planId, UUID recipeId, String title,
                         double portions, Nutrients nutrition, LocalDateTime recordedAt) {}
    public record DailyIntake(LocalDate date, List<Intake> meals, Nutrients totals) {}

    private static final java.time.ZoneId VN_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
    private User owner(String principal) { return access.user(principal); }
    private void validDate(LocalDate date) {
        LocalDate todayVn = LocalDate.now(VN_ZONE);
        // Allow up to +1 day to comfortably accommodate client local timezone differences (e.g. past midnight in UTC+7)
        LocalDate maxDate = todayVn.plusDays(1);
        if (date == null || date.isAfter(maxDate) || date.isBefore(todayVn.minusYears(1)))
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Chỉ ghi lại bữa ăn từ hôm nay đến một năm trước.");
    }
    private double round(double value) { return Math.round(value * 10) / 10.0; }
    private Intake response(MealIntake meal) {
        return new Intake(meal.getId(), meal.getDate(), meal.getSlot(), meal.getPlanId(), meal.getRecipeId(),
                meal.getTitle(), meal.getPortions(), new Nutrients(meal.getCalories(), meal.getProtein(),
                meal.getCarbs(), meal.getFat(), meal.getFiber()), meal.getRecordedAt());
    }
    @Transactional(readOnly = true)
    public DailyIntake day(String principal, LocalDate date) {
        validDate(date);
        List<Intake> meals = intakes.findByUserIdAndDateOrderBySlot(owner(principal).getUserId(), date)
                .stream().map(this::response).toList();
        Nutrients total = new Nutrients(
                round(meals.stream().mapToDouble(m -> m.nutrition().calories()).sum()),
                round(meals.stream().mapToDouble(m -> m.nutrition().protein()).sum()),
                round(meals.stream().mapToDouble(m -> m.nutrition().carbs()).sum()),
                round(meals.stream().mapToDouble(m -> m.nutrition().fat()).sum()),
                round(meals.stream().mapToDouble(m -> m.nutrition().fiber()).sum()));
        return new DailyIntake(date, meals, total);
    }
    @Transactional
    public DailyIntake record(String principal, LocalDate date, int slot, double portions) {
        validDate(date);
        if (slot < 0 || slot > 5 || !Double.isFinite(portions) || portions < 0.25 || portions > 5)
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Khẩu phần thực tế phải từ 0,25 đến 5.");
        User user = owner(principal);
        CalendarDay planDay = plans.calendar(principal, date, date).stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Ngày này chưa có thực đơn đã lưu."));
        if (planDay.needsReview()) throw new AppException(ErrorCode.COMMUNITY_CONFLICT,
                "Hồ sơ dinh dưỡng đã thay đổi. Hãy tạo thực đơn mới trước khi ghi bữa ăn.");
        Meal planned = planDay.meals().stream().filter(m -> m.slot() == slot).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Bữa ăn không có trong thực đơn."));
        MealIntake saved = intakes.findByUserIdAndDateAndSlot(user.getUserId(), date, slot).orElseGet(MealIntake::new);
        double ratio = portions / planned.portions();
        saved.setUserId(user.getUserId()); saved.setDate(date); saved.setSlot(slot);
        saved.setPlanId(planDay.planId()); saved.setRecipeId(planned.recipeId()); saved.setTitle(planned.title());
        saved.setPortions(portions); saved.setCalories(round(planned.nutrition().calories() * ratio));
        saved.setProtein(round(planned.nutrition().protein() * ratio));
        saved.setCarbs(round(planned.nutrition().carbs() * ratio));
        saved.setFat(round(planned.nutrition().fat() * ratio));
        saved.setFiber(round(planned.nutrition().fiber() * ratio)); saved.setRecordedAt(LocalDateTime.now());
        intakes.saveAndFlush(saved);
        return day(principal, date);
    }
    @Transactional
    public DailyIntake remove(String principal, LocalDate date, int slot) {
        validDate(date);
        intakes.findByUserIdAndDateAndSlot(owner(principal).getUserId(), date, slot).ifPresent(intakes::delete);
        return day(principal, date);
    }
}
