package com.vegeai.backend.modules.recipe.service;

import com.vegeai.backend.modules.recipe.entity.*;
import com.vegeai.backend.modules.recipe.repository.RecipeRepository;
import com.vegeai.backend.modules.recipe.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;

/**
 * Khởi tạo dữ liệu mẫu mặc định cho Hệ thống Thẻ nhãn đa tầng và Kho công thức chuẩn của Admin.
 * Tự động chạy khi khởi động nếu cơ sở dữ liệu chưa có tags.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecipeDataInitializer implements CommandLineRunner {

    private final TagRepository tagRepository;
    private final RecipeRepository recipeRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (tagRepository.count() == 0) {
            log.info("[RECIPE-INIT] Khởi tạo bộ từ điển thẻ tag ẩm thực chay phân tầng...");
            initTags();
        }

        if (recipeRepository.count() < 7) {
            log.info("[RECIPE-INIT] Khởi tạo các công thức chuẩn mẫu (Verified Recipes)...");
            initSampleRecipes();
        }
    }

    private void initTags() {
        List<Tag> tags = List.of(
                // 1. DIETARY_TYPE (Trường phái ăn chay)
                Tag.builder().code("VEGAN").name("Thuần chay (Vegan)").group(TagGroup.DIETARY_TYPE).description("100% nguồn gốc thực vật, không trứng, sữa, mật ong").build(),
                Tag.builder().code("LACTO_OVO").name("Chay trứng sữa (Lacto-Ovo)").group(TagGroup.DIETARY_TYPE).description("Chế độ ăn thực vật có kết hợp trứng và sữa").build(),
                Tag.builder().code("OVO").name("Chay có trứng (Ovo)").group(TagGroup.DIETARY_TYPE).description("Chế độ ăn thực vật có sử dụng trứng").build(),
                Tag.builder().code("LACTO").name("Chay có sữa (Lacto)").group(TagGroup.DIETARY_TYPE).description("Chế độ ăn thực vật có sử dụng sữa và chế phẩm từ sữa").build(),
                Tag.builder().code("NO_FIVE_PUNGENT").name("Không ngũ vị tân").group(TagGroup.DIETARY_TYPE).description("Loại bỏ hành, hẹ, tỏi, kiệu, nén theo truyền thống Phật giáo").build(),
                Tag.builder().code("RAW_VEGAN").name("Chay thô (Raw Vegan)").group(TagGroup.DIETARY_TYPE).description("Thực phẩm tươi sống tự nhiên, không qua xử lý nhiệt trên 48°C").build(),

                // 2. COOKING_METHOD (Phương pháp chế biến)
                Tag.builder().code("BRAISED").name("Món kho").group(TagGroup.COOKING_METHOD).description("Kho tộ, kho tiêu, kho gừng đậm đà").build(),
                Tag.builder().code("STIR_FRY").name("Món xào").group(TagGroup.COOKING_METHOD).description("Xào lăn, xào thập cẩm, xào chua ngọt giòn ngon").build(),
                Tag.builder().code("FRIED").name("Món chiên giòn").group(TagGroup.COOKING_METHOD).description("Chiên xù, chiên cốm vàng ươm giòn rụm").build(),
                Tag.builder().code("STEAMED").name("Món hấp / Luộc").group(TagGroup.COOKING_METHOD).description("Thanh đạm, bảo toàn tối đa vi chất và vị ngọt tự nhiên").build(),

                // 3. DISH_CATEGORY (Loại món ăn)
                Tag.builder().code("SOUP").name("Món canh / Soup").group(TagGroup.DISH_CATEGORY).description("Canh thanh mát, soup bổ dưỡng cân bằng mâm cơm").build(),
                Tag.builder().code("HOTPOT").name("Lẩu chay").group(TagGroup.DISH_CATEGORY).description("Lẩu nấm, lẩu chua cay, lẩu rau củ ấm cúng").build(),
                Tag.builder().code("SALAD").name("Gỏi / Salad").group(TagGroup.DISH_CATEGORY).description("Gỏi trộn, salad rau mầm thanh mát giàu enzyme").build(),
                Tag.builder().code("NOODLE").name("Bún / Phở / Mì").group(TagGroup.DISH_CATEGORY).description("Món nước truyền thống phong cách chay thanh ngọt").build(),

                // 4. NUTRITION_GOAL (Mục tiêu dinh dưỡng & Sức khỏe)
                Tag.builder().code("HIGH_PROTEIN").name("Giàu đạm thực vật").group(TagGroup.NUTRITION_GOAL).description("Cung cấp từ 12g protein thực vật trở lên mỗi phần giúp chống teo cơ").build(),
                Tag.builder().code("LOW_CALORIE").name("Giảm cân / Ít calo").group(TagGroup.NUTRITION_GOAL).description("Dưới 300 kcal mỗi khẩu phần, giàu chất xơ tạo cảm giác no lâu").build(),
                Tag.builder().code("IRON_RICH").name("Bổ máu / Giàu sắt").group(TagGroup.NUTRITION_GOAL).description("Giàu sắt thực vật (rau bina, mè đen, đậu hạt) hỗ trợ tái tạo hồng cầu").build(),
                Tag.builder().code("HEART_HEALTHY").name("Tốt cho tim mạch").group(TagGroup.NUTRITION_GOAL).description("Giàu chất béo không bão hòa đơn/đa, kiểm soát natri và không cholesterol").build(),
                Tag.builder().code("LOW_GI").name("Tiểu đường / Low-GI").group(TagGroup.NUTRITION_GOAL).description("Carbohydrate phức giải phóng chậm, kiểm soát ổn định đường huyết").build(),
                Tag.builder().code("DIGESTIVE").name("Dễ tiêu hóa").group(TagGroup.NUTRITION_GOAL).description("Thanh nhẹ, dễ hấp thu, êm dịu dạ dày và hỗ trợ lợi khuẩn đường ruột").build(),

                // 5. OCCASION (Thời gian và hoàn cảnh nấu)
                Tag.builder().code("QUICK_15").name("Nhanh dưới 15 phút").group(TagGroup.OCCASION).description("Tổng thời gian sơ chế và nấu hoàn tất dưới 15 phút cho người bận rộn").build(),
                Tag.builder().code("UNDER_30").name("Dưới 30 phút").group(TagGroup.OCCASION).description("Bữa cơm gia đình tiêu chuẩn hoàn thành trong 30 phút").build(),
                Tag.builder().code("ONE_POT").name("Nấu 1 nồi (One-Pot)").group(TagGroup.OCCASION).description("Tiện lợi, nấu trọn trong 1 nồi/chảo, tối giản dọn rửa").build(),
                Tag.builder().code("BUDGET").name("Tiết kiệm / Sinh viên").group(TagGroup.OCCASION).description("Chi phí nguyên liệu bình dân, dễ mua ở chợ truyền thống").build(),
                Tag.builder().code("MEAL_PREP").name("Meal Prep đi làm").group(TagGroup.OCCASION).description("Bảo quản tốt trong hộp kín ngăn mát 2-3 ngày hoặc cấp đông").build(),

                // 6. DIETARY_RESTRICTION (Dị ứng và thành phần loại trừ - Exclusion)
                Tag.builder().code("SOY_FREE").name("Không đậu nành (Soy-Free)").group(TagGroup.DIETARY_RESTRICTION).description("Không sử dụng đậu phụ, tàu hũ ky hay tương đậu nành").build(),
                Tag.builder().code("GLUTEN_FREE").name("Không gluten (Gluten-Free)").group(TagGroup.DIETARY_RESTRICTION).description("Không chứa gluten, không dùng mì căn hay bột mì").build(),
                Tag.builder().code("NUT_FREE").name("Không đậu phộng & hạt cây (Nut-Free)").group(TagGroup.DIETARY_RESTRICTION).description("Không dùng đậu phộng và các loại hạt cây (hạnh nhân, óc chó, hạt điều)").build(),
                Tag.builder().code("MUSHROOM_FREE").name("Không nấm").group(TagGroup.DIETARY_RESTRICTION).description("Không chứa bất kỳ loại nấm nào (cho người dị ứng nấm)").build(),
                Tag.builder().code("NOT_SPICY").name("Không cay").group(TagGroup.DIETARY_RESTRICTION).description("Không tiêu, không ớt, an toàn cho trẻ em và người đau dạ dày").build()
        );

        tagRepository.saveAll(tags);
    }

    private void initSampleRecipes() {
        Map<String, Tag> tagMap = new HashMap<>();
        tagRepository.findAll().forEach(t -> tagMap.put(t.getCode(), t));

        // Món 1: Đậu phụ sốt cà chua tươi
        Recipe r1 = Recipe.builder()
                .title("Đậu phụ sốt cà chua tươi")
                .slug("dau-phu-sot-ca-chua-tuoi")
                .description("Món ăn thanh đạm, giàu đạm thực vật, sốt cà chua đậm đà rất đưa cơm.")
                .prepTimeMinutes(5)
                .cookTimeMinutes(15)
                .servings(2)
                .difficulty(DifficultyLevel.EASY)
                .calories(240)
                .proteinGrams(14.5)
                .carbsGrams(16.0)
                .fatGrams(8.5)
                .fiberGrams(3.8)
                .imageUrl("https://images.unsplash.com/photo-1546069901-ba9599a7e63c")
                .instructions(List.of(
                        "Đậu phụ cắt khối vuông vừa ăn, thấm khô nước rồi chiên vàng đều các mặt.",
                        "Cà chua rửa sạch, thái hạt lựu, phi thơm với một chút dầu ăn cho mềm nhuyễn thành sốt.",
                        "Cho đậu phụ vào đảo nhẹ cùng sốt cà chua, nêm hạt nêm chay và xì dầu vừa ăn trong 5 phút.",
                        "Rắc một chút hành hoa hoặc ngò rí lên trên và thưởng thức nóng với cơm trắng."
                ))
                .isVerified(true)
                .status(RecipeStatus.PUBLISHED)
                .tags(collectTags(tagMap, "VEGAN", "STIR_FRY", "HIGH_PROTEIN", "UNDER_30", "BUDGET", "GLUTEN_FREE", "NOT_SPICY"))
                .build();

        r1.addIngredient(RecipeIngredient.builder().name("Đậu phụ non").normalizedName("dau phu non").amount(200.0).unit("g").isOptional(false).isAllergen(true).allergenType("SOY").build());
        r1.addIngredient(RecipeIngredient.builder().name("Cà chua").normalizedName("ca chua").amount(2.0).unit("quả").isOptional(false).build());
        r1.addIngredient(RecipeIngredient.builder().name("Hành hoa").normalizedName("hanh hoa").amount(1.0).unit("nhánh").isOptional(true).build());

        // Món 2: Canh cải ngọt nấu nấm bào ngư
        Recipe r2 = Recipe.builder()
                .title("Canh cải ngọt nấu nấm bào ngư")
                .slug("canh-cai-ngot-nau-nam-bao-ngu")
                .description("Canh rau nấm ngọt thanh tự nhiên, giàu chất xơ và khoáng chất thanh nhiệt cơ thể.")
                .prepTimeMinutes(10)
                .cookTimeMinutes(15)
                .servings(3)
                .difficulty(DifficultyLevel.EASY)
                .calories(110)
                .proteinGrams(6.2)
                .carbsGrams(12.5)
                .fatGrams(1.8)
                .fiberGrams(4.5)
                .imageUrl("https://images.unsplash.com/photo-1547592180-85f173990554")
                .instructions(List.of(
                        "Cải ngọt rửa sạch, cắt khúc 4-5 cm. Nấm bào ngư xé sợi vừa ăn, ngâm nước muối loãng 5 phút.",
                        "Đun sôi 600ml nước, cho nấm bào ngư vào nấu sôi trong 3 phút để tiết vị ngọt thanh.",
                        "Cho tiếp cải ngọt vào nấu sôi bùng trong 2 phút, nêm muối hồng và hạt nêm nấm.",
                        "Tắt bếp ngay khi rau vừa chín tới để giữ màu xanh mướt và độ giòn ngọt."
                ))
                .isVerified(true)
                .status(RecipeStatus.PUBLISHED)
                .tags(collectTags(tagMap, "VEGAN", "SOUP", "LOW_CALORIE", "UNDER_30", "SOY_FREE", "GLUTEN_FREE", "NOT_SPICY"))
                .build();

        r2.addIngredient(RecipeIngredient.builder().name("Cải ngọt").normalizedName("cai ngot").amount(1.0).unit("bó").isOptional(false).build());
        r2.addIngredient(RecipeIngredient.builder().name("Nấm bào ngư trắng").normalizedName("nam bao ngu trang").amount(150.0).unit("g").isOptional(false).build());
        r2.addIngredient(RecipeIngredient.builder().name("Gừng tươi").normalizedName("gung tuoi").amount(1.0).unit("lát").isOptional(true).build());

        // Món 3: Nấm đùi gà xào sả ớt giòn thơm
        Recipe r3 = Recipe.builder()
                .title("Nấm đùi gà xào sả ớt giòn thơm")
                .slug("nam-dui-ga-xao-sa-ot-gion-thom")
                .description("Món xào thơm lừng mùi sả, vị cay nồng kích thích vị giác và giàu đạm thực vật.")
                .prepTimeMinutes(5)
                .cookTimeMinutes(12)
                .servings(2)
                .difficulty(DifficultyLevel.EASY)
                .calories(165)
                .proteinGrams(7.8)
                .carbsGrams(15.2)
                .fatGrams(3.4)
                .fiberGrams(5.1)
                .imageUrl("https://images.unsplash.com/photo-1540420773420-3366772f4999")
                .instructions(List.of(
                        "Nấm đùi gà rửa sạch, thái lát xéo dày khoảng 0.5 cm.",
                        "Phi thơm sả băm và ớt hiểm với dầu thực vật cho vàng thơm.",
                        "Cho nấm đùi gà vào xào trên lửa lớn trong 5-7 phút cho nấm săn lại và thấm vị sả.",
                        "Nêm nước tương tamari và một chút tiêu đen, đảo đều rồi tắt bếp."
                ))
                .isVerified(true)
                .status(RecipeStatus.PUBLISHED)
                .tags(collectTags(tagMap, "VEGAN", "STIR_FRY", "QUICK_15", "HIGH_PROTEIN", "SOY_FREE", "GLUTEN_FREE"))
                .build();

        r3.addIngredient(RecipeIngredient.builder().name("Nấm đùi gà").normalizedName("nam dui ga").amount(200.0).unit("g").isOptional(false).build());
        r3.addIngredient(RecipeIngredient.builder().name("Sả băm").normalizedName("sa bam").amount(2.0).unit("muỗng canh").isOptional(false).build());
        r3.addIngredient(RecipeIngredient.builder().name("Ớt hiểm").normalizedName("ot hiem").amount(1.0).unit("quả").isOptional(true).build());

                // Món 4: Cà ri đậu gà khoai lang cốt dừa
        Recipe r4 = Recipe.builder()
                .title("Cà ri đậu gà khoai lang cốt dừa")
                .slug("ca-ri-dau-ga-khoai-lang-cot-dua")
                .description("Món cà ri sánh mịn, thơm bùi ngậy từ nước cốt dừa và khoai lang mật, giàu đạm thực vật từ đậu gà.")
                .prepTimeMinutes(15)
                .cookTimeMinutes(25)
                .servings(4)
                .difficulty(DifficultyLevel.MEDIUM)
                .calories(380)
                .proteinGrams(14.2)
                .carbsGrams(48.0)
                .fatGrams(12.5)
                .fiberGrams(8.5)
                .imageUrl("https://images.unsplash.com/photo-1546833999-b9f581a1996d")
                .instructions(List.of(
                        "Đậu gà ngâm mềm luộc chín tới. Khoai lang và cà rốt gọt vỏ, cắt khối vuông vừa ăn.",
                        "Phi thơm hành boa-rô băm và bột cà ri hữu cơ với 1 muỗng dầu dừa cho dậy mùi thơm nức.",
                        "Cho khoai lang, cà rốt và đậu gà vào đảo đều trong 3 phút cho ngấm gia vị.",
                        "Thêm 400ml nước dừa tươi và 150ml nước cốt dừa, đun nhỏ lửa trong 15 phút đến khi khoai dẻo ngọt sánh mịn."
                ))
                .isVerified(true)
                .status(RecipeStatus.PUBLISHED)
                .tags(collectTags(tagMap, "VEGAN", "ONE_POT", "HIGH_PROTEIN", "HEART_HEALTHY", "GLUTEN_FREE", "SOY_FREE"))
                .build();

        r4.addIngredient(RecipeIngredient.builder().name("Đậu gà luộc").normalizedName("dau ga luoc").amount(250.0).unit("g").isOptional(false).build());
        r4.addIngredient(RecipeIngredient.builder().name("Khoai lang mật").normalizedName("khoai lang mat").amount(200.0).unit("g").isOptional(false).build());
        r4.addIngredient(RecipeIngredient.builder().name("Nước cốt dừa").normalizedName("nuoc cot dua").amount(150.0).unit("ml").isOptional(false).build());
        r4.addIngredient(RecipeIngredient.builder().name("Bột cà ri").normalizedName("bot ca ri").amount(1.0).unit("gói").isOptional(false).build());

        // Món 5: Canh rong biển đậu hũ hạt sen dưỡng tâm
        Recipe r5 = Recipe.builder()
                .title("Canh rong biển đậu hũ hạt sen dưỡng tâm")
                .slug("canh-rong-bien-dau-hu-hat-sen-duong-tam")
                .description("Vị ngọt thanh khiết từ hạt sen tươi và rong biển giàu iot, khoáng chất biển sâu giúp an thần ngủ ngon.")
                .prepTimeMinutes(10)
                .cookTimeMinutes(20)
                .servings(3)
                .difficulty(DifficultyLevel.EASY)
                .calories(160)
                .proteinGrams(9.8)
                .carbsGrams(22.0)
                .fatGrams(3.5)
                .fiberGrams(4.8)
                .imageUrl("https://images.unsplash.com/photo-1547592180-85f173990554")
                .instructions(List.of(
                        "Hạt sen tươi thông tâm, rửa sạch. Rong biển khô ngâm nước lọc 10 phút cho nở mềm rồi vớt ra cắt khúc.",
                        "Ninh hạt sen với 800ml nước cùng 2 lát gừng trong 15 phút cho hạt sen chín bở bùi ngọt.",
                        "Cho đậu hũ non cắt miếng vuông và rong biển vào nấu sôi thêm 3 phút.",
                        "Nêm muối hồng và dầu mè nguyên chất, tắt bếp rắc chút tiêu sọ xay."
                ))
                .isVerified(true)
                .status(RecipeStatus.PUBLISHED)
                .tags(collectTags(tagMap, "VEGAN", "SOUP", "DIGESTIVE", "HEART_HEALTHY", "LOW_CALORIE", "NOT_SPICY"))
                .build();

        r5.addIngredient(RecipeIngredient.builder().name("Hạt sen tươi").normalizedName("hat sen tuoi").amount(100.0).unit("g").isOptional(false).build());
        r5.addIngredient(RecipeIngredient.builder().name("Rong biển khô").normalizedName("rong bien kho").amount(20.0).unit("g").isOptional(false).build());
        r5.addIngredient(RecipeIngredient.builder().name("Đậu hũ non").normalizedName("dau hu non").amount(1.0).unit("bìa").isOptional(false).isAllergen(true).allergenType("SOY").build());

        // Món 6: Gỏi cuốn ngũ sắc tương bơ đậu phộng
        Recipe r6 = Recipe.builder()
                .title("Gỏi cuốn ngũ sắc tương bơ đậu phộng")
                .slug("goi-cuon-ngu-sac-tuong-bo-dau-phong")
                .description("Món cuốn tươi mát trọn vẹn chất chống oxy hóa từ bắp cải tím, ớt chuông, bơ chín, xà lách giòn và sốt bơ lạc.")
                .prepTimeMinutes(15)
                .cookTimeMinutes(5)
                .servings(2)
                .difficulty(DifficultyLevel.EASY)
                .calories(280)
                .proteinGrams(11.5)
                .carbsGrams(34.0)
                .fatGrams(10.2)
                .fiberGrams(6.2)
                .imageUrl("https://images.unsplash.com/photo-1540420773420-3366772f4999")
                .instructions(List.of(
                        "Bắp cải tím, dưa leo, cà rốt và ớt chuông bào sợi mỏng. Quả bơ thái lát dài.",
                        "Đậu hũ khuôn thái thanh dài chiên vàng đều.",
                        "Làm ướt bánh tráng gạo lứt, xếp rau thơm, bắp cải, đậu hũ, bơ vào rồi cuộn chặt tay.",
                        "Pha sốt chấm: Trộn đều bơ đậu phộng mịn, tương tamari, nước cốt chanh và một chút nước ấm đến khi sánh ngậy."
                ))
                .isVerified(true)
                .status(RecipeStatus.PUBLISHED)
                .tags(collectTags(tagMap, "RAW_VEGAN", "SALAD", "QUICK_15", "LOW_CALORIE", "HEART_HEALTHY", "GLUTEN_FREE"))
                .build();

        r6.addIngredient(RecipeIngredient.builder().name("Bánh tráng gạo lứt").normalizedName("banh trang gao lut").amount(1.0).unit("tập").isOptional(false).build());
        r6.addIngredient(RecipeIngredient.builder().name("Quả bơ sáp").normalizedName("qua bo sap").amount(1.0).unit("quả").isOptional(false).build());
        r6.addIngredient(RecipeIngredient.builder().name("Bắp cải tím").normalizedName("bap cai tim").amount(100.0).unit("g").isOptional(false).build());
        r6.addIngredient(RecipeIngredient.builder().name("Bơ đậu phộng").normalizedName("bo dau phong").amount(2.0).unit("muỗng canh").isOptional(false).isAllergen(true).allergenType("PEANUTS").build());

        // Món 7: Đậu khuôn kho nấm đông cô tiêu xanh
        Recipe r7 = Recipe.builder()
                .title("Đậu khuôn kho nấm đông cô tiêu xanh")
                .slug("dau-khuon-kho-nam-dong-co-tieu-xanh")
                .description("Đậu hũ chiên vàng giòn thấm đẫm gia vị mặn ngọt hài hòa, nấm đông cô dai ngọt cùng chùm tiêu xanh thơm lừng.")
                .prepTimeMinutes(10)
                .cookTimeMinutes(20)
                .servings(3)
                .difficulty(DifficultyLevel.EASY)
                .calories(220)
                .proteinGrams(15.0)
                .carbsGrams(14.5)
                .fatGrams(9.0)
                .fiberGrams(5.0)
                .imageUrl("https://images.unsplash.com/photo-1546069901-ba9599a7e63c")
                .instructions(List.of(
                        "Đậu hũ khuôn cắt miếng vuông chiên vàng giòn 4 mặt.",
                        "Nấm đông cô ngâm mềm rửa sạch, khía chữ thập trên mũ nấm.",
                        "Cho đậu hũ và nấm vào tộ đất, ướp cùng nước tương tamari, mật mía, tiêu xanh đập dập trong 10 phút.",
                        "Bắc tộ lên bếp đun sôi, thêm 100ml nước dừa, kho nhỏ lửa 15 phút cho nước keo lại óng ánh."
                ))
                .isVerified(true)
                .status(RecipeStatus.PUBLISHED)
                .tags(collectTags(tagMap, "VEGAN", "BRAISED", "HIGH_PROTEIN", "BUDGET", "UNDER_30"))
                .build();

        r7.addIngredient(RecipeIngredient.builder().name("Đậu hũ khuôn").normalizedName("dau hu khuon").amount(3.0).unit("bìa").isOptional(false).isAllergen(true).allergenType("SOY").build());
        r7.addIngredient(RecipeIngredient.builder().name("Nấm đông cô tươi").normalizedName("nam dong co tuoi").amount(150.0).unit("g").isOptional(false).build());
        r7.addIngredient(RecipeIngredient.builder().name("Tiêu xanh").normalizedName("tieu xanh").amount(2.0).unit("chùm").isOptional(false).build());

        List<Recipe> allRecipes = List.of(r1, r2, r3, r4, r5, r6, r7);
        for (Recipe r : allRecipes) {
            if (!recipeRepository.existsBySlug(r.getSlug())) {
                recipeRepository.save(r);
            }
        }
    }

    private Set<Tag> collectTags(Map<String, Tag> tagMap, String... codes) {
        Set<Tag> result = new HashSet<>();
        for (String code : codes) {
            Tag t = tagMap.get(code);
            if (t != null) {
                result.add(t);
            }
        }
        return result;
    }
}
