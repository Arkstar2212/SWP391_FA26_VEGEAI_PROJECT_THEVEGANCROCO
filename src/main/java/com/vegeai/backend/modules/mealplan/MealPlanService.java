package com.vegeai.backend.modules.mealplan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.*;
import com.vegeai.backend.modules.ai.service.GeminiAiService;
import com.vegeai.backend.modules.community.service.CommunityAccess;
import com.vegeai.backend.modules.nutrition.dto.*;
import com.vegeai.backend.modules.nutrition.service.NutritionService;
import com.vegeai.backend.modules.notification.service.NotificationService;
import com.vegeai.backend.modules.recipe.entity.*;
import com.vegeai.backend.modules.recipe.dto.RecipeSummaryDto;
import com.vegeai.backend.modules.recipe.repository.RecipeRepository;
import com.vegeai.backend.modules.recipe.service.RecipeService;
import com.vegeai.backend.modules.user.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.vegeai.backend.modules.mealplan.MealPlanDtos.*;

@Service @RequiredArgsConstructor
public class MealPlanService {
    private final MealPlanRepository plans;
    private final RecipeRepository recipes;
    private final RecipeService recipeService;
    private final RecipeDietPolicy dietPolicy;
    private final NutritionService nutrition;
    private final CommunityAccess access;
    private final GeminiAiService ai;
    private final ObjectMapper mapper;
    private final NotificationService notificationService;
    private User member(String principal) {
        User user = access.user(principal);
        if (user.getRole() != UserRole.MEMBER) throw new AppException(ErrorCode.FORBIDDEN);
        return user;
    }
    private NutritionProfileResponse profile(String principal, User user) {
        if (!user.isProfileCompleted()) throw new AppException(ErrorCode.PROFILE_INCOMPLETE);
        return nutrition.getMyProfile(principal);
    }
    private boolean complete(Recipe r) {
        return r.getTags().stream().noneMatch(tag -> Set.of("CONDIMENT", "DESSERT", "SIDE_DISH").contains(tag.getCode()))
                && r.getCalories() != null && r.getCalories() > 0 && r.getServings() != null && r.getServings() > 0
                && valid(r.getProteinGrams()) && valid(r.getCarbsGrams()) && valid(r.getFatGrams()) && valid(r.getFiberGrams())
                && !r.getInstructions().isEmpty() && !r.getIngredients().isEmpty()
                && r.getIngredients().stream().allMatch(i -> i.getAmount() != null && Double.isFinite(i.getAmount()) && i.getAmount() > 0 && i.getUnit() != null && !i.getUnit().isBlank());
    }
    private boolean valid(Double value) { return value != null && Double.isFinite(value) && value >= 0; }
    private List<Recipe> eligible(User user) {
        return recipes.findByStatus(RecipeStatus.PUBLISHED).stream().filter(this::complete).filter(r -> dietPolicy.allowed(r, user))
                .sorted(Comparator.comparing(r -> r.getRecipeId().toString())).toList();
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize meal plan", ex); }
    }
    private Snapshot snapshot(MealPlan plan) {
        try { return mapper.readValue(plan.getSnapshot(), Snapshot.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot read meal plan", ex); }
    }
    private String signature(NutritionProfileResponse p) {
        String input = json(List.of(p.getGoal(), p.getActivityLevel(), p.getTargets(), Objects.toString(p.getDietaryPreferences(), ""), Objects.toString(p.getAllergies(), "")));
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private View view(MealPlan plan) { return new View(plan.getId(), plan.getStatus(), plan.getCreatedAt(), plan.getSavedAt(), snapshot(plan)); }
    private MealPlan owned(UUID id, User user, boolean lock) {
        return (lock ? plans.lockOwned(id, user.getUserId()) : plans.findByIdAndUserId(id, user.getUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private void editable(MealPlan plan, NutritionProfileResponse profile) {
        if (!"DRAFT".equals(plan.getStatus())) throw new AppException(ErrorCode.COMMUNITY_CONFLICT, "Kế hoạch đã lưu không thể sửa. Hãy tạo kế hoạch mới.");
        if (!plan.getProfileSignature().equals(signature(profile))) throw new AppException(ErrorCode.COMMUNITY_CONFLICT, "Hồ sơ dinh dưỡng đã thay đổi. Vui lòng tạo lại kế hoạch.");
    }
    @Transactional
    public View generate(String principal, Generate request) {
        User user = member(principal); var profile = profile(principal, user);
        if (request.startDate().isBefore(LocalDate.now().minusDays(1)) || request.startDate().isAfter(LocalDate.now().plusYears(1)))
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Ngày bắt đầu phải từ hôm nay và trong một năm tới.");
        List<Recipe> available = eligible(user).stream().limit(60).toList();
        if (available.isEmpty()) throw new AppException(ErrorCode.VALIDATION_ERROR, "Chưa có công thức đủ định lượng, dinh dưỡng và phù hợp chế độ ăn/dị ứng của bạn. Vui lòng kiểm tra hồ sơ hoặc bổ sung thư viện công thức.");
        List<Map<String, Object>> candidates = available.stream().map(r -> Map.<String,Object>of(
                "recipeId", r.getRecipeId(), "title", r.getTitle(), "calories", r.getCalories(), "protein", r.getProteinGrams(),
                "carbs", r.getCarbsGrams(), "fat", r.getFatGrams(), "hasVideo", r.getVideos().stream().anyMatch(v -> v.getStatus() == com.vegeai.backend.modules.community.entity.VideoStatus.PUBLISHED))).toList();
        List<UUID> ranked = ai.rankMealPlanRecipes(json(Map.of("task", "VEGEAI_MEAL_PLAN", "targets", profile.getTargets(),
                "goal", profile.getGoal(), "diet", user.getDietaryPreferences(), "allergies", Objects.toString(user.getAllergies(), "NONE"), "recipes", candidates)));
        Map<UUID, Recipe> byId = new HashMap<>(); available.forEach(r -> byId.put(r.getRecipeId(), r));
        if (ranked.stream().anyMatch(id -> !byId.containsKey(id))) throw new AppException(ErrorCode.AI_RESPONSE_INVALID);
        List<Recipe> ordered = new ArrayList<>(); new LinkedHashSet<>(ranked).forEach(id -> ordered.add(byId.get(id)));
        available.stream().filter(r -> !ranked.contains(r.getRecipeId())).forEach(ordered::add);
        LocalDate end = "WEEK".equals(request.period()) ? request.startDate().plusDays(7) : request.startDate().plusMonths(1);
        List<Day> days = new ArrayList<>(); Map<UUID,Integer> usage = new HashMap<>();
        for (LocalDate date = request.startDate(); date.isBefore(end); date = date.plusDays(1)) {
            List<Meal> meals = new ArrayList<>();
            for (int slot = 0; slot < request.mealsPerDay(); slot++) {
                Recipe chosen = null; double best = Double.MAX_VALUE;
                for (int index = 0; index < ordered.size(); index++) {
                    Recipe candidate = ordered.get(index);
                    double portions = portions(candidate, profile.getTargets().getDailyCaloriesTarget() / (double) request.mealsPerDay());
                    double cost = cost(candidate, portions, profile.getTargets(), request.mealsPerDay())
                            + usage.getOrDefault(candidate.getRecipeId(),0) * 0.08 + index * 0.002
                            + (meals.stream().anyMatch(m -> m.recipeId().equals(candidate.getRecipeId())) ? 0.8 : 0);
                    if (cost < best) { best = cost; chosen = candidate; }
                }
                meals.add(meal(slot, chosen, profile.getTargets(), request.mealsPerDay()));
                usage.merge(chosen.getRecipeId(), 1, Integer::sum);
            }
            days.add(new Day(date, meals, sum(meals)));
        }
        Snapshot snap = new Snapshot(request.period(), request.startDate(), end.minusDays(1), request.mealsPerDay(), profile.getTargets(), days, warnings(days, profile.getTargets(), available.size()));
        MealPlan plan = new MealPlan(); plan.setUserId(user.getUserId()); plan.setStatus("DRAFT"); plan.setSnapshot(json(snap)); plan.setProfileSignature(signature(profile));
        MealPlan created = plans.saveAndFlush(plan);
        // Generation is a user-requested activity and is confirmed in the in-app notification center.
        notificationService.notifyUser(user, "MEAL_PLAN_GENERATED", "Thực đơn cá nhân đã được tạo",
                "Thực đơn nháp của bạn đã sẵn sàng để xem và chỉnh sửa.", "/meal-plans/" + created.getId());
        return view(created);
    }
    private double portions(Recipe recipe, double calories) { return Math.max(0.5, Math.min(3, Math.round(calories / recipe.getCalories() * 4) / 4.0)); }
    private double cost(Recipe r, double portions, NutritionTargetsResponse t, int meals) {
        return Math.abs(r.getCalories() * portions * meals / t.getDailyCaloriesTarget() - 1)
                + Math.abs(r.getProteinGrams() * portions * meals / Math.max(1, t.getProteinTargetG()) - 1) * .35
                + Math.abs(r.getCarbsGrams() * portions * meals / Math.max(1, t.getCarbsTargetG()) - 1) * .2
                + Math.abs(r.getFatGrams() * portions * meals / Math.max(1, t.getFatTargetG()) - 1) * .2;
    }
    private Meal meal(int slot, Recipe r, NutritionTargetsResponse target, int meals) {
        double p = portions(r, target.getDailyCaloriesTarget() / (double) meals);
        return new Meal(slot, r.getRecipeId(), r.getTitle(), r.getImageUrl(), p,
                new Nutrients(round(r.getCalories()*p), round(r.getProteinGrams()*p), round(r.getCarbsGrams()*p), round(r.getFatGrams()*p), round(r.getFiberGrams()*p)), recipeService.mapToDetailResponse(r));
    }
    private double round(double n) { return Math.round(n*10)/10.0; }
    private Nutrients sum(List<Meal> meals) {
        return new Nutrients(round(meals.stream().mapToDouble(m->m.nutrition().calories()).sum()), round(meals.stream().mapToDouble(m->m.nutrition().protein()).sum()),
                round(meals.stream().mapToDouble(m->m.nutrition().carbs()).sum()), round(meals.stream().mapToDouble(m->m.nutrition().fat()).sum()), round(meals.stream().mapToDouble(m->m.nutrition().fiber()).sum()));
    }
    private List<String> warnings(List<Day> days, NutritionTargetsResponse target, int count) {
        List<String> messages = new ArrayList<>(); messages.add("Dinh dưỡng dự kiến từ dữ liệu công thức, không phải lượng đã ăn. Kiểm tra nhãn nguyên liệu và nguy cơ lây nhiễm chéo nếu có dị ứng.");
        if (count < 7) messages.add("Thư viện món phù hợp còn ít; kế hoạch có thể lặp lại món.");
        if (days.stream().anyMatch(d -> Math.abs(d.totals().calories()/target.getDailyCaloriesTarget()-1) > .15
                || Math.abs(d.totals().protein()/Math.max(1,target.getProteinTargetG())-1) > .25
                || Math.abs(d.totals().carbs()/Math.max(1,target.getCarbsTargetG())-1) > .25
                || Math.abs(d.totals().fat()/Math.max(1,target.getFatTargetG())-1) > .25))
            messages.add("Một số ngày chưa đạt mục tiêu calo hoặc macro. Xem tổng từng ngày và đổi món trước khi lưu; không xem đây là kế hoạch dinh dưỡng y khoa.");
        if (days.stream().flatMap(d->d.meals().stream()).anyMatch(m->m.recipe().getVideos().isEmpty())) messages.add("Một số công thức chưa có video liên kết; hướng dẫn nấu bằng chữ vẫn có sẵn.");
        return messages;
    }
    @Transactional(readOnly=true) public View get(String principal, UUID id) { return view(owned(id, member(principal), false)); }
    private record Effective(MealPlan plan, Snapshot snapshot, TreeSet<LocalDate> visible) {}
    /** Saved plans newest first, each with the dates no later-saved plan overrides (the latest save wins a date). */
    private List<Effective> effectivePlans(User user) {
        List<MealPlan> saved = plans.findByUserIdAndStatusOrderBySavedAtAscIdAsc(user.getUserId(), "SAVED");
        Set<LocalDate> covered = new HashSet<>(); List<Effective> result = new ArrayList<>();
        for (int i = saved.size() - 1; i >= 0; i--) {
            Snapshot s = snapshot(saved.get(i)); TreeSet<LocalDate> visible = new TreeSet<>();
            for (Day d : s.days()) if (covered.add(d.date())) visible.add(d.date());
            result.add(new Effective(saved.get(i), s, visible));
        }
        return result;
    }
    /** Saved plans that still own at least one calendar day; plans fully overridden by later saves are hidden. */
    @Transactional(readOnly=true) public PageResponse<Summary> list(String principal, int page, int size) {
        User user = member(principal);
        if (page < 0 || size < 1 || size > 20) throw new AppException(ErrorCode.VALIDATION_ERROR);
        List<Effective> shown = effectivePlans(user).stream().filter(e -> !e.visible().isEmpty()).toList();
        int from = (int) Math.min((long) page * size, shown.size());
        List<Effective> slice = shown.subList(from, Math.min(from + size, shown.size()));
        Page<Effective> result = new PageImpl<>(slice, PageRequest.of(page, size), shown.size());
        return PageResponse.from(result, slice.stream().map(e -> { var s = e.snapshot(); var p = e.plan();
            return new Summary(p.getId(), s.period(), s.startDate(), s.endDate(), s.mealsPerDay(), p.getSavedAt(),
                    s.days().size(), e.visible().size(), e.visible().first(), e.visible().last()); }).toList());
    }
    /** Saved plans merged onto one calendar; where plans overlap, the most recently saved plan wins. */
    @Transactional(readOnly=true) public List<CalendarDay> calendar(String principal, LocalDate from, LocalDate to) {
        User user = member(principal);
        if (from.isAfter(to) || java.time.temporal.ChronoUnit.DAYS.between(from, to) > 62)
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Khoảng ngày của lịch không hợp lệ (tối đa 62 ngày).");
        Map<LocalDate, CalendarDay> byDate = new TreeMap<>();
        for (Effective e : effectivePlans(user)) {
            Snapshot s = e.snapshot();
            if (s.endDate().isBefore(from) || s.startDate().isAfter(to)) continue;
            for (Day d : s.days())
                if (e.visible().contains(d.date()) && !d.date().isBefore(from) && !d.date().isAfter(to))
                    byDate.put(d.date(), new CalendarDay(d.date(), e.plan().getId(), s.period(), s.targets(), d.meals(), d.totals()));
        }
        return List.copyOf(byDate.values());
    }
    @Transactional(readOnly=true) public List<RecipeSummaryDto> options(String principal, UUID id) {
        User user = member(principal); MealPlan plan = owned(id,user,false); editable(plan,profile(principal,user));
        return eligible(user).stream().limit(60).map(recipeService::mapToSummaryDto).toList();
    }
    @Transactional public View swap(String principal, UUID id, int day, int slot, UUID recipeId) {
        User user = member(principal); MealPlan plan = owned(id,user,true); var p=profile(principal,user); editable(plan,p);
        Snapshot s=snapshot(plan);
        if(day<0||day>=s.days().size()||slot<0||slot>=s.mealsPerDay()) throw new AppException(ErrorCode.VALIDATION_ERROR);
        Recipe r=eligible(user).stream().filter(v->v.getRecipeId().equals(recipeId)).findFirst().orElseThrow(()->new AppException(ErrorCode.VALIDATION_ERROR,"Món không còn phù hợp hoặc thiếu dữ liệu dinh dưỡng."));
        List<Day> days=new ArrayList<>(s.days()); List<Meal> meals=new ArrayList<>(days.get(day).meals()); meals.set(slot,meal(slot,r,p.getTargets(),s.mealsPerDay()));
        days.set(day,new Day(days.get(day).date(),meals,sum(meals)));
        plan.setSnapshot(json(new Snapshot(s.period(),s.startDate(),s.endDate(),s.mealsPerDay(),s.targets(),days,warnings(days,s.targets(),eligible(user).size()))));
        return view(plans.saveAndFlush(plan));
    }
    @Transactional public View save(String principal, UUID id) {
        User user=member(principal); MealPlan plan=owned(id,user,true);
        if("SAVED".equals(plan.getStatus())) return view(plan);
        var p=profile(principal,user); editable(plan,p); Snapshot s=snapshot(plan);
        Map<UUID,Recipe> current=new HashMap<>(); eligible(user).forEach(r->current.put(r.getRecipeId(),r));
        List<Day> days=new ArrayList<>();
        for(Day day:s.days()) {
            List<Meal> meals=new ArrayList<>();
            for(Meal old:day.meals()) {
                Recipe r=current.get(old.recipeId()); if(r==null) throw new AppException(ErrorCode.COMMUNITY_CONFLICT,"Công thức đã thay đổi hoặc không còn phù hợp. Hãy tạo lại kế hoạch.");
                meals.add(meal(old.slot(),r,p.getTargets(),s.mealsPerDay()));
            }
            days.add(new Day(day.date(),meals,sum(meals)));
        }
        plan.setSnapshot(json(new Snapshot(s.period(),s.startDate(),s.endDate(),s.mealsPerDay(),s.targets(),days,warnings(days,s.targets(),current.size()))));
        plan.setStatus("SAVED"); plan.setSavedAt(LocalDateTime.now());
        MealPlan saved = plans.saveAndFlush(plan);
        // Saving a draft is a distinct activity; already-saved plans return above without duplicate notices.
        notificationService.notifyUser(user, "MEAL_PLAN_SAVED", "Thực đơn đã được lưu",
                "Thực đơn của bạn đã được lưu vào danh sách kế hoạch cá nhân.", "/meal-plans/" + saved.getId());
        return view(saved);
    }
}
