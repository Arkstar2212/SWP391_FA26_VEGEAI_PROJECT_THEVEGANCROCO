package com.vegeai.backend.modules.mealplan;
import com.vegeai.backend.modules.recipe.entity.Recipe;
import com.vegeai.backend.modules.user.entity.User;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.*;

@Component
public class RecipeDietPolicy {
    private static final List<List<String>> ALLERGENS = List.of(
        List.of("peanuts", "peanut", "dau phong", "lac"),
        List.of("tree nuts", "hat dinh duong", "hat dieu", "hanh nhan", "oc cho", "cashew", "almond", "walnut"),
        List.of("soy", "soya", "dau nanh", "dau tuong", "dau phu", "dau hu", "tofu", "nuoc tuong", "xi dau", "tempeh"),
        List.of("gluten", "wheat", "lua mi", "bot mi", "mi can", "seitan", "banh mi", "nuoc tuong", "xi dau"),
        List.of("dairy", "milk", "sua", "pho mai", "cheese", "butter", "bo sua", "cream", "yogurt"),
        List.of("eggs", "egg", "trung"), List.of("sesame", "me", "vung"),
        List.of("mushrooms", "mushroom", "nam"), List.of("corn", "bap", "ngo"));
    private String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replaceAll("[^a-z0-9]+", " ").trim();
    }
    private boolean contains(String text, String term) { return (" " + text + " ").contains(" " + term + " "); }
    public boolean allowed(Recipe recipe, User user) {
        String diet = user.getDietaryPreferences();
        if (diet == null || diet.isBlank()) return false;
        Set<String> excluded = new HashSet<>();
        for (String declared : Optional.ofNullable(user.getAllergies()).orElse("").split("[,;\\n]")) {
            String term = normalize(declared);
            if (term.isEmpty() || term.equals("none")) continue;
            excluded.add(term);
            ALLERGENS.stream().filter(group -> group.stream().anyMatch(alias -> contains(term, alias)))
                    .forEach(excluded::addAll);
        }
        if (Set.of("VEGAN", "RAW_VEGAN", "LACTO").contains(diet)) excluded.addAll(ALLERGENS.get(5));
        if (Set.of("VEGAN", "RAW_VEGAN", "OVO").contains(diet)) excluded.addAll(ALLERGENS.get(4));
        // VEGEAI plans remain vegetarian even for a flexitarian profile.
        excluded.addAll(List.of("thit bo", "thit heo", "thit lon", "thit ga", "thit vit", "thit ca", "ca hoi", "ca ngu", "tom", "cua", "hai san", "nuoc mam", "mo dong vat", "pork", "beef", "chicken", "fish", "shrimp"));
        if ("RAW_VEGAN".equals(diet) && (recipe.getCookTimeMinutes() == null || recipe.getCookTimeMinutes() > 0)) return false;
        return recipe.getIngredients().stream().noneMatch(ingredient -> {
            String text = normalize(ingredient.getName() + " " + Optional.ofNullable(ingredient.getAllergenType()).orElse(""));
            return excluded.stream().anyMatch(term -> contains(text, term));
        });
    }
}
