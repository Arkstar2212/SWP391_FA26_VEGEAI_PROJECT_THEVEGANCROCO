package com.vegeai.backend.modules.mealplan;

import com.vegeai.backend.modules.mealplan.SeasonalMealDtos.*;
import com.vegeai.backend.modules.recipe.entity.Recipe;
import com.vegeai.backend.modules.recipe.entity.RecipeStatus;
import com.vegeai.backend.modules.recipe.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeasonalMealService {

    private final RecipeRepository recipeRepository;

    public SeasonalRegionalResponse getRecommendations(Season requestedSeason, Region requestedRegion) {
        Season season = requestedSeason != null ? requestedSeason : detectCurrentSeason();
        Region region = requestedRegion != null ? requestedRegion : Region.MIEN_NAM;

        String seasonNameVi = switch (season) {
            case XUAN -> "Mùa Xuân (Tươi mới - Sinh sôi)";
            case HA -> "Mùa Hạ (Nắng ấm - Bù nước thanh mát)";
            case THU -> "Mùa Thu (Dịu mát - Trong lành)";
            case DONG -> "Mùa Đông (Se lạnh - Bổ dưỡng & Giữ ấm)";
        };

        String regionNameVi = switch (region) {
            case MIEN_BAC -> "Miền Bắc (Hương vị thanh tao, cân bằng hài hòa)";
            case MIEN_TRUNG -> "Miền Trung (Đậm đà, hương vị nồng ấm đặc trưng)";
            case MIEN_NAM -> "Miền Nam (Thanh mát, tươi ngọt từ rau củ quả nhiệt đới)";
            case MIEN_TAY -> "Miền Tây sông nước (Đặc sản hoa đồng cỏ nội, mùa nước nổi)";
        };

        String climateSummary = buildClimateSummary(season, region);
        List<String> produce = buildHighlightedProduce(season, region);
        String nutritionAdvice = buildNutritionAdvice(season, region);
        List<SeasonalMealSuggestion> rawSuggestions = buildSuggestions(season, region);

        // Attach matched recipes if found in database
        List<SeasonalMealSuggestion> suggestions = new ArrayList<>();
        for (SeasonalMealSuggestion s : rawSuggestions) {
            UUID matchedId = findMatchingRecipeId(s.dishName());
            suggestions.add(new SeasonalMealSuggestion(
                    s.mealType(),
                    s.dishName(),
                    s.seasonalIngredients(),
                    s.healthBenefits(),
                    s.regionalHighlights(),
                    s.estimatedCalories(),
                    s.cookingTip(),
                    matchedId
            ));
        }

        return new SeasonalRegionalResponse(
                season,
                seasonNameVi,
                region,
                regionNameVi,
                climateSummary,
                produce,
                nutritionAdvice,
                suggestions,
                "Gợi ý mang tính chất tham khảo dinh dưỡng theo mùa vụ, không thay thế tư vấn y khoa chuyên nghiệp."
        );
    }

    private Season detectCurrentSeason() {
        int month = LocalDate.now().getMonthValue();
        if (month >= 1 && month <= 3) return Season.XUAN;
        if (month >= 4 && month <= 6) return Season.HA;
        if (month >= 7 && month <= 9) return Season.THU;
        return Season.DONG;
    }

    private String buildClimateSummary(Season season, Region region) {
        return switch (region) {
            case MIEN_BAC -> switch (season) {
                case XUAN -> "Tiết trời ẩm ướt, mưa xuân; cơ thể thích hợp với thức ăn ấm nhẹ, kích thích vị giác và giàu vitamin.";
                case HA -> "Nắng hè oi ả; cần ưu tiên thực phẩm có tính mát, nhiều nước để thanh nhiệt và bù khoáng tự nhiên.";
                case THU -> "Thời tiết heo may mát mẻ, hanh nhẹ; món ăn thanh đạm, giàu ẩm và củ quả tươi giúp cơ thể dễ chịu.";
                case DONG -> "Không khí lạnh buốt; cơ thể cần nhiều đạm thực vật, món kho hầm ấm với gừng, quế, tiêu giúp giữ ấm cơ thể.";
            };
            case MIEN_TRUNG -> switch (season) {
                case XUAN -> "Tiết trời giao mùa ấm dần; thực đơn bổ sung rau xanh tơ, nấm và các loại củ quả tươi đầu năm.";
                case HA -> "Nắng rát và gió khô nóng; thích hợp các món canh chua thanh, chè đậu mát lành bù nước.";
                case THU, DONG -> "Mùa mưa kéo dài, không khí ẩm se lạnh; chuộng món kho sả tiêu ớt đậm đà, giúp làm ấm bụng và ngon miệng.";
            };
            case MIEN_NAM -> switch (season) {
                case HA, XUAN -> "Mùa khô nắng ấm quanh năm; thích hợp các loại rau trái thanh mát, canh chua bông súng, canh mướp mồng tơi.";
                case THU, DONG -> "Mùa mưa rải rác; các món kho quẹt nấm, canh củ quả hầm đậm vị đem lại cảm giác ấm cúng gia đình.";
            };
            case MIEN_TAY -> switch (season) {
                case THU, DONG -> "Mùa nước nổi đem lại nguồn sản vật tự nhiên trù phú: bông điên điển, bông so đũa, ngó sen, nấm rơm đồng rộ nhất.";
                case XUAN, HA -> "Mùa khô trù phú trái cây và rau thơm miệt vườn; chuộng các món cuốn, canh chua thanh mát nhẹ bụng.";
            };
        };
    }

    private List<String> buildHighlightedProduce(Season season, Region region) {
        List<String> list = new ArrayList<>();
        switch (region) {
            case MIEN_BAC -> {
                if (season == Season.DONG || season == Season.THU) {
                    list.addAll(List.of("Cải bắp Sapa", "Su hào Hà Giang", "Củ sen Hồ Tây", "Nấm hương Sa Pa", "Cà rốt Hải Dương"));
                } else {
                    list.addAll(List.of("Măng tre tươi Yên Bái", "Rau ngót vườn", "Mướp hương", "Đậu cô ve Mộc Châu", "Rau muống tiến vua"));
                }
            }
            case MIEN_TRUNG -> {
                if (season == Season.HA) {
                    list.addAll(List.of("Mít non Quảng Nam", "Rau đắng biển", "Khổ qua rừng Quảng Trị", "Đậu ván xanh", "Hạt sen Huế"));
                } else {
                    list.addAll(List.of("Nấm tràm Thừa Thiên", "Măng le Tây Nguyên", "Sả ớt tiêu Phong Điền", "Khoai môn sáp", "Rau dớn"));
                }
            }
            case MIEN_NAM -> {
                list.addAll(List.of("Đậu rồng Đồng Nai", "Khổ qua xanh Nam Bộ", "Mồng tơi rau đay", "Bí đỏ hồ lô", "Nấm bào ngư xám"));
            }
            case MIEN_TAY -> {
                list.addAll(List.of("Bông điên điển mùa nước nổi", "Bông súng đồng Tháp Mười", "Ngó sen Đồng Tháp", "Nấm rơm Cần Thơ", "Bông so đũa"));
            }
        }
        return list;
    }

    private String buildNutritionAdvice(Season season, Region region) {
        if (season == Season.DONG) {
            return "Khuyến nghị mùa đông: Ưu tiên đạm thực vật từ đậu hạt hầm, tăng nhẹ gia vị ấm tự nhiên (gừng, tiêu sọ, sả) để giữ ấm cơ thể.";
        }
        if (season == Season.HA) {
            return "Khuyến nghị mùa hè: Bổ sung 2.5 - 3 lít nước/ngày kèm các loại canh thanh mát, trái cây mọng nước để bù điện giải.";
        }
        return "Khuyến nghị dinh dưỡng: Tận dụng tối đa rau củ thu hoạch đúng vụ để hấp thu lượng vitamin và chất chống oxy hóa cao nhất.";
    }

    private List<SeasonalMealSuggestion> buildSuggestions(Season season, Region region) {
        List<SeasonalMealSuggestion> list = new ArrayList<>();

        if (region == Region.MIEN_BAC && (season == Season.DONG || season == Season.THU)) {
            list.add(new SeasonalMealSuggestion(
                    "Bữa sáng",
                    "Phở nấm hương Hà Nội & Quẩy chay giòn",
                    "Bánh phở tươi, nấm hương rừng Sa Pa, hồi, quế chi, thảo quả nướng, hành hoa",
                    "Nước dùng thanh ngọt tự nhiên, cung cấp năng lượng ấm áp đầu ngày.",
                    "Hương vị phở cổ truyền đất kinh kỳ, nước dùng ninh từ củ quả ngọt dịu.",
                    360,
                    "Nướng thơm hành khô và gừng trước khi thả vào nồi nước dùng để dậy hương thảo mộc."
            ));
            list.add(new SeasonalMealSuggestion(
                    "Bữa trưa",
                    "Cơm gạo lứt, Đậu hũ kho nấm hương tiêu sọ & Canh bắp cải cuộn đậu hũ",
                    "Đậu hũ mơ nướng vàng, nấm đông cô, bắp cải Sapa, tiêu sọ đập dập, tương bần",
                    "Cung cấp đạm thực vật hoàn chỉnh và chất xơ hòa tan giúp no lâu.",
                    "Nổi bật vị béo ngậy của đậu mơ Hà Nội hòa quyện hương nấm đông cô.",
                    480,
                    "Chần sơ lá bắp cải để cuộn đậu hũ không bị rách, hấp cách thủy giữ trọn vị ngọt."
            ));
            list.add(new SeasonalMealSuggestion(
                    "Bữa tối",
                    "Canh củ sen hầm táo đỏ hạt sen & Cải ngọt xào nấm đùi gà",
                    "Củ sen tươi Hồ Tây, hạt sen khô, táo đỏ, nấm đùi gà baby, dầu mè",
                    "Giàu magie, kali và chất xơ, giúp thư giãn tinh thần và mang lại giấc ngủ dễ chịu.",
                    "Món canh dưỡng sinh truyền thống thanh đạm của người Hà Nội mùa trở gió.",
                    380,
                    "Ninh củ sen lửa nhỏ 40 phút cho bở tơi, thêm táo đỏ sau cùng để nước canh trong vắt."
            ));
            list.add(new SeasonalMealSuggestion(
                    "Thức uống / Tráng miệng",
                    "Trà gừng mật ong thực vật & Hạt dẻ Trùng Khánh nướng",
                    "Gừng tươi đập dập, cỏ ngọt/mật mía Nghệ An, hạt dẻ Cao Bằng nướng than",
                    "Làm ấm họng, hỗ trợ tiêu hóa nhẹ nhàng sau bữa tối mùa se lạnh.",
                    "Món quà vặt mùa đông đặc trưng miền rẻo cao phía Bắc.",
                    120,
                    "Nướng hạt dẻ khía chữ thập đến khi vỏ nứt bung tỏa mùi bùi thơm ngào ngạt."
            ));
            return list;
        }

        if (region == Region.MIEN_TAY) {
            list.add(new SeasonalMealSuggestion(
                    "Bữa sáng",
                    "Hủ tiếu Sa Đéc rau củ & Nấm rơm tươi đồng",
                    "Sợi hủ tiếu dai Sa Đéc, nấm rơm búp, củ cải trắng, cà rốt, hẹ tươi, giá sống",
                    "Bổ sung carbohydrate phức hợp và vitamin nhóm B từ nấm tươi dồi dào.",
                    "Sợi hủ tiếu bột gạo Sa Đéc danh tiếng trứ danh miền Tây.",
                    350,
                    "Trụng sợi hủ tiếu nhanh qua nước sôi rồi xóc chút dầu tỏi phi để sợi tơi giòn."
            ));
            list.add(new SeasonalMealSuggestion(
                    "Bữa trưa",
                    "Canh chua bông điên điển nấm rơm & Đậu hũ kho tương hạt",
                    "Bông điên điển vàng rộ, me chua dầm, nấm rơm tươi, đậu hũ chiên xắt miếng, ngò om",
                    "Hỗ trợ giải nhiệt, dồi dào vitamin C và đạm thực vật từ đậu tương lên men tự nhiên.",
                    "Hương vị mùa nước nổi châu thổ sông Cửu Long không thể trộn lẫn.",
                    460,
                    "Bông điên điển chỉ cho vào nồi canh khi vừa tắt bếp để giữ nguyên độ giòn ngọt thơm."
            ));
            list.add(new SeasonalMealSuggestion(
                    "Bữa tối",
                    "Gỏi ngó sen tôm chay hạt sen & Bánh phồng đậu xanh nướng",
                    "Ngó sen non Đồng Tháp, đậu phộng rang giã dập, rau răm, nước mắm chay chua ngọt",
                    "Giàu chất xơ, vitamin C và khoáng chất, lượng calo thấp lý tưởng cho bữa tối nhẹ bụng.",
                    "Món gỏi thanh tao trên mâm tiệc miền sông nước miệt vườn.",
                    320,
                    "Ngâm ngó sen với nước chanh loãng đá lạnh để ngó sen giữ màu trắng muốt và giòn sần sật."
            ));
            list.add(new SeasonalMealSuggestion(
                    "Tráng miệng",
                    "Chè đậu xanh nha đam đường phèn thanh mát",
                    "Đậu xanh cà vỏ, nha đam tươi sơ chế sạch nhớt, lá dứa thơm, đường phèn thanh",
                    "Cung cấp chất chống oxy hóa tự nhiên, giúp làm dịu cơ thể sau ngày hoạt động.",
                    "Món tráng miệng thanh mát quen thuộc của người Nam Bộ.",
                    160,
                    "Rửa nha đam với muối và chanh, ngâm nước đá trước khi nấu để nha đam giòn sần sật."
            ));
            return list;
        }

        if (region == Region.MIEN_TRUNG) {
            list.add(new SeasonalMealSuggestion(
                    "Bữa sáng",
                    "Bún bò Huế chay thơm nồng sả ớt & Chả lụa phù trúc",
                    "Sợi bún to Huế, tàu hũ ky tươi cuộn chả, sả cây đập dập, dầu màu điều, rau thơm kinh giới",
                    "Hương vị nồng ấm, giàu đạm thực vật từ đậu nành giúp khởi đầu ngày mới tỉnh táo.",
                    "Đặc sắc ẩm thực cung đình và chay niệm xứ Huế.",
                    410,
                    "Phi thơm sả ớt cùng dầu hạt điều cho màu đỏ cam óng ánh dậy mùi thơm."
            ));
            list.add(new SeasonalMealSuggestion(
                    "Bữa trưa",
                    "Mít non kho rau răm & Canh nấm tràm/rau ngót đậu hũ",
                    "Mít non luộc chín xắt lát, rau răm tươi, đậu phộng rang giã dập, nấm tươi",
                    "Nhiều chất xơ hòa tan, chất béo lành mạnh từ đậu phộng và đạm thực vật từ đậu hũ.",
                    "Món chay quen thuộc trong mọi mâm cơm truyền thống miền Trung.",
                    490,
                    "Kho mít non liu riu với nước tương ngon đến khi mít dẻo thấm vị bùi ngậy."
            ));
            list.add(new SeasonalMealSuggestion(
                    "Bữa tối",
                    "Cơm gạo lứt muối mè & Nấm rơm kho tiêu đen cay ấm",
                    "Nấm rơm búp tươi, tiêu sọ đập dập, mè đen rang thơm, dưa leo tươi giòn",
                    "Giàu khoáng chất tự nhiên, dễ tiêu hóa cho bữa tối thanh nhẹ ấm cúng.",
                    "Tiêu sọ xứ Quảng cay thơm ấm nồng đặc trưng.",
                    400,
                    "Để lửa liu riu cho nấm rơm tiết nước ngọt rồi sắc lại bóng bẩy."
            ));
            list.add(new SeasonalMealSuggestion(
                    "Thức uống / Dưỡng sinh",
                    "Trà atiso đỏ (Hibiscus) hạt sen thanh nhiệt",
                    "Hoa atiso đỏ tươi, hạt sen tươi, mật ong thực vật, cỏ ngọt",
                    "Giàu vitamin C và chất chống oxy hóa tự nhiên từ thảo mộc tươi mát.",
                    "Món quà thảo mộc miền Trung thanh khiết.",
                    85,
                    "Uống ấm hoặc ướp mát đều giữ nguyên hương hoa nồng nàn dịu dàng."
            ));
            return list;
        }

        // Default: MIEN_NAM
        list.add(new SeasonalMealSuggestion(
                "Bữa sáng",
                "Bánh mì bì chay nấm tuyết & Sữa đậu nành mè đen",
                "Bánh mì giòn, bì chay từ củ sắn & miến dong, thính gạo rang, dưa chua, đậu nành tươi",
                "Cung cấp carbohydrate phức hợp và năng lượng bền bỉ suốt buổi sáng năng động.",
                "Ẩm thực đường phố Sài Gòn biến tấu thuần chay thơm ngon trọn vị.",
                390,
                "Trộn thính gạo vàng đều tay khi bì ráo nước để tạo độ tơi thơm xốp."
        ));
        list.add(new SeasonalMealSuggestion(
                "Bữa trưa",
                "Cơm hạt sen, Đậu hũ non sốt nấm đông cô & Canh khổ qua nhồi đậu hũ",
                "Đậu hũ non mịn, khổ qua xanh, nấm đông cô tươi, nấm mèo, hạt sen, hành ngò thơm",
                "Thanh mát, giàu đạm thực vật và chất xơ, mang lại cảm giác nhẹ bụng và sảng khoái.",
                "Món canh khổ qua tượng trưng cho sự thanh lành may mắn của người phương Nam.",
                510,
                "Chần sơ khổ qua qua nước sôi trước khi nấu để giữ màu xanh mướt và giảm bớt vị đắng gắt."
        ));
        list.add(new SeasonalMealSuggestion(
                "Bữa tối",
                "Canh bí đỏ đậu phộng & Nấm bào ngư xào hạt điều",
                "Bí đỏ hồ lô, đậu phộng tươi giã dập, nấm bào ngư xám, hạt điều Bình Phước giòn bùi",
                "Bổ sung khoáng chất, tryptophan tự nhiên hỗ trợ cơ thể thư giãn cho giấc ngủ sâu.",
                "Sự hòa quyện giữa nấm ngọt và vị béo bùi hạt điều Đông Nam Bộ.",
                430,
                "Cho hạt điều vào sau cùng lúc đảo nhanh tay để giữ trọn độ giòn thơm rụm."
        ));
        list.add(new SeasonalMealSuggestion(
                "Thức uống / Tráng miệng",
                "Nước sâm mía lau rong biển lá dứa",
                "Mía lau, rễ tranh, rong biển thanh nhiệt, lá dứa thơm, la hán quả, đường phèn thanh",
                "Giải khát tuyệt vời, bù nước và khoáng chất tự nhiên sau ngày hoạt động.",
                "Thức uống thảo mộc danh tiếng khắp phố phường Nam Bộ.",
                90,
                "Nấu lửa nhỏ 45 phút rồi để nguội, uống thanh đạm không cần thêm quá nhiều đường."
        ));
        return list;
    }

    private UUID findMatchingRecipeId(String dishName) {
        if (dishName == null || dishName.isBlank()) return null;
        try {
            List<Recipe> published = recipeRepository.findByStatus(RecipeStatus.PUBLISHED);
            String normalizedDish = dishName.toLowerCase();
            for (Recipe r : published) {
                if (r.getTitle() == null) continue;
                String title = r.getTitle().toLowerCase();
                if (normalizedDish.contains(title) || title.contains(normalizedDish)) {
                    return r.getRecipeId();
                }
            }
        } catch (Exception e) {
            log.debug("Recipe matching skipped: {}", e.getMessage());
        }
        return null;
    }
}
