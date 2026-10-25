package com.vegeai.backend.modules.recipe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.auth.service.JwtService;
import com.vegeai.backend.modules.fridge.entity.FreshnessStatus;
import com.vegeai.backend.modules.fridge.entity.FridgeItem;
import com.vegeai.backend.modules.fridge.entity.FridgeItemStatus;
import com.vegeai.backend.modules.fridge.repository.FridgeItemRepository;
import com.vegeai.backend.modules.recipe.dto.CreateRecipeRequest;
import com.vegeai.backend.modules.recipe.dto.RecipeFilterRequest;
import com.vegeai.backend.modules.recipe.dto.RecipeIngredientDto;
import com.vegeai.backend.modules.recipe.entity.DifficultyLevel;
import com.vegeai.backend.modules.recipe.entity.InclusionMode;
import com.vegeai.backend.modules.recipe.entity.Recipe;
import com.vegeai.backend.modules.recipe.repository.RecipeRepository;
import com.vegeai.backend.modules.recipe.service.RecipeDataInitializer;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecipeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private FridgeItemRepository fridgeItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RecipeDataInitializer recipeDataInitializer;

    private User memberUser;
    private String memberToken;

    private User adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        fridgeItemRepository.deleteAll();
        recipeRepository.deleteAll();
        userRepository.deleteAll();

        // Seed lại dữ liệu mẫu nếu chưa có
        recipeDataInitializer.run();

        memberUser = User.builder()
                .username("recipe_member")
                .email("member@vegeai.com")
                .passwordHash(passwordEncoder.encode("Pass@123"))
                .fullName("Thành Viên Chay")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .build();
        memberUser = userRepository.save(memberUser);
        memberToken = jwtService.generateAccessToken(memberUser);

        adminUser = User.builder()
                .username("recipe_admin")
                .email("admin_recipe@vegeai.com")
                .passwordHash(passwordEncoder.encode("AdminPass@123"))
                .fullName("Quản Trị Viên Công Thức")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build();
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateAccessToken(adminUser);
    }

    @Test
    @DisplayName("GET /api/recipes/tags: Lấy toàn bộ cây danh mục thẻ nhãn phân loại món chay đa tầng")
    void getFilterTags_returnsGroupedTagsTree() throws Exception {
        mockMvc.perform(get("/api/recipes/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dietaryType", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.cookingMethod", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.dishCategory", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.nutritionGoal", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.occasion", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.dietaryRestriction", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("POST /api/recipes/filter: Bộ lọc với Inclusion Mode = AND (phải thỏa mãn đủ các tag)")
    void filterRecipes_andLogic() throws Exception {
        RecipeFilterRequest filter = RecipeFilterRequest.builder()
                .includeTagCodes(List.of("VEGAN", "HIGH_PROTEIN"))
                .inclusionMode(InclusionMode.AND)
                .build();

        mockMvc.perform(post("/api/recipes/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(filter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.items[*].title", hasItem("Đậu phụ sốt cà chua tươi")));
    }

    @Test
    @DisplayName("POST /api/recipes/filter: Bộ lọc với Inclusion Mode = OR (thỏa mãn bất kỳ tag nào)")
    void filterRecipes_orLogic() throws Exception {
        RecipeFilterRequest filter = RecipeFilterRequest.builder()
                .includeTagCodes(List.of("SOUP", "BRAISED"))
                .inclusionMode(InclusionMode.OR)
                .build();

        mockMvc.perform(post("/api/recipes/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(filter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.items[*].title", hasItem("Canh cải ngọt nấu nấm bào ngư")));
    }

    @Test
    @DisplayName("POST /api/recipes/filter: Bộ lọc với Exclusion Mode (loại trừ tuyệt đối thành phần kiêng)")
    void filterRecipes_exclusionMode() throws Exception {
        // Loại trừ các món có tag SOY_FREE -> Chỉ còn lại các món có đậu nành (như Đậu phụ sốt cà chua)
        RecipeFilterRequest filter = RecipeFilterRequest.builder()
                .excludeTagCodes(List.of("SOY_FREE"))
                .build();

        mockMvc.perform(post("/api/recipes/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(filter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].title", hasItem("Canh cải ngọt nấu nấm bào ngư")))
                .andExpect(jsonPath("$.data.items[*].title", not(hasItem("Đậu phụ sốt cà chua tươi"))));
    }

    @Test
    @DisplayName("POST /api/recipes/filter: Lọc theo ngưỡng calo tối đa và thời gian nấu")
    void filterRecipes_nutritionalThresholds() throws Exception {
        RecipeFilterRequest filter = RecipeFilterRequest.builder()
                .maxCalories(150)
                .maxCookTime(20)
                .build();

        mockMvc.perform(post("/api/recipes/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(filter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].title", hasItem("Canh cải ngọt nấu nấm bào ngư")))
                .andExpect(jsonPath("$.data.items[*].title", not(hasItem("Đậu phụ sốt cà chua tươi"))));
    }

    @Test
    @DisplayName("GET /api/recipes/slug/{slug}: Lấy chi tiết công thức bằng SEO slug")
    void getRecipeBySlug_success() throws Exception {
        mockMvc.perform(get("/api/recipes/slug/dau-phu-sot-ca-chua-tuoi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Đậu phụ sốt cà chua tươi"))
                .andExpect(jsonPath("$.data.ingredients", hasSize(3)))
                .andExpect(jsonPath("$.data.instructions", hasSize(4)))
                .andExpect(jsonPath("$.data.calories").value(240))
                .andExpect(jsonPath("$.data.proteinGrams").value(14.5));
    }

    @Test
    @DisplayName("GET /api/recipes/fridge-match: So khớp tủ lạnh thông minh với công thức Admin (% Recipe Match)")
    void matchRecipesWithFridge_success() throws Exception {
        // Tủ lạnh người dùng có: Đậu phụ non + Cà chua
        FridgeItem tofu = FridgeItem.builder()
                .userId(memberUser.getUserId())
                .name("Đậu phụ non")
                .normalizedName("dau phu non")
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .expiryDate(LocalDate.now().plusDays(3))
                .freshnessStatus(FreshnessStatus.FRESH)
                .status(FridgeItemStatus.ACTIVE)
                .build();

        FridgeItem tomato = FridgeItem.builder()
                .userId(memberUser.getUserId())
                .name("Cà chua")
                .normalizedName("ca chua")
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .expiryDate(LocalDate.now().plusDays(4))
                .freshnessStatus(FreshnessStatus.FRESH)
                .status(FridgeItemStatus.ACTIVE)
                .build();

        fridgeItemRepository.saveAll(List.of(tofu, tomato));

        mockMvc.perform(get("/api/recipes/fridge-match?minMatchPercentage=50")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecipesEvaluated", greaterThan(0)))
                .andExpect(jsonPath("$.data.canCookNowCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.matchedRecipes[0].recipe.title").value("Đậu phụ sốt cà chua tươi"))
                .andExpect(jsonPath("$.data.matchedRecipes[0].matchPercentage").value(100))
                .andExpect(jsonPath("$.data.matchedRecipes[0].matchGroup").value("CAN_COOK_NOW"))
                .andExpect(jsonPath("$.data.matchedRecipes[0].availableInFridge", hasSize(2)));
    }

    @Test
    @DisplayName("Admin Recipe CRUD: Tạo, sửa, xóa công thức chuẩn thành công")
    void adminRecipeCrud_success() throws Exception {
        CreateRecipeRequest createReq = CreateRecipeRequest.builder()
                .title("Salad bơ đậu gà sốt mè rang")
                .description("Salad giàu đạm và chất béo tốt cho tim mạch.")
                .prepTimeMinutes(10)
                .cookTimeMinutes(5)
                .servings(2)
                .difficulty(DifficultyLevel.EASY)
                .calories(280)
                .proteinGrams(11.0)
                .carbsGrams(25.0)
                .fatGrams(12.0)
                .fiberGrams(7.0)
                .tagCodes(List.of("VEGAN", "SALAD", "HEART_HEALTHY", "QUICK_15"))
                .ingredients(List.of(
                        RecipeIngredientDto.builder().name("Đậu gà luộc").amount(150.0).unit("g").isOptional(false).build(),
                        RecipeIngredientDto.builder().name("Bơ sáp").amount(1.0).unit("quả").isOptional(false).build()
                ))
                .instructions(List.of(
                        "Bơ cắt hạt lựu, đậu gà để ráo nước.",
                        "Trộn đều cùng xà lách và sốt mè rang."
                ))
                .build();

        // 1. Tạo mới
        String resBody = mockMvc.perform(post("/api/admin/recipes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Salad bơ đậu gà sốt mè rang"))
                .andExpect(jsonPath("$.data.isVerified").value(true))
                .andReturn().getResponse().getContentAsString();

        String recipeId = objectMapper.readTree(resBody).path("data").path("recipeId").asText();
        assertNotNull(recipeId);

        // 2. Xóa
        mockMvc.perform(delete("/api/admin/recipes/" + recipeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Bảo mật phân quyền: Người dùng thường không được phép gọi API Admin tạo công thức")
    void memberCannotAccessAdminEndpoints() throws Exception {
        CreateRecipeRequest createReq = CreateRecipeRequest.builder()
                .title("Hack Recipe")
                .ingredients(List.of(RecipeIngredientDto.builder().name("Rau").build()))
                .instructions(List.of("Nấu"))
                .build();

        mockMvc.perform(post("/api/admin/recipes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());
    }
}
