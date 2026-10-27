package com.vegeai.backend.modules.recipe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.support.GeminiIntegrationTestSupport;
import com.vegeai.backend.modules.ai.dto.GenerateRecipeAiRequest;
import com.vegeai.backend.modules.auth.service.JwtService;
import com.vegeai.backend.modules.recipe.dto.SaveCookbookRecipeRequest;
import com.vegeai.backend.modules.recipe.dto.UpdateCookbookRecipeRequest;
import com.vegeai.backend.modules.recipe.entity.CustomRecipe;
import com.vegeai.backend.modules.recipe.repository.CustomRecipeRepository;
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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CookbookControllerTest extends GeminiIntegrationTestSupport {
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.vegeai.backend.modules.community.service.CommunityDataInitializer communityDataInitializer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomRecipeRepository customRecipeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User userA;
    private String tokenA;

    private User userB;
    private String tokenB;

    @BeforeEach
    void setUp() {
        customRecipeRepository.deleteAll();
        userRepository.deleteAll();

        userA = User.builder()
                .username("chef_user_a")
                .email("chef_a@vegeai.com")
                .passwordHash(passwordEncoder.encode("Pass@123"))
                .fullName("Đầu Bếp Ăn Chay A")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .dietaryPreferences("VEGAN")
                .allergies("NONE")
                .build();
        userA = userRepository.save(userA);
        tokenA = jwtService.generateAccessToken(userA);

        userB = User.builder()
                .username("chef_user_b")
                .email("chef_b@vegeai.com")
                .passwordHash(passwordEncoder.encode("Pass@123"))
                .fullName("Đầu Bếp Ăn Chay B")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .dietaryPreferences("LACTO_OVO")
                .allergies("PEANUTS")
                .build();
        userB = userRepository.save(userB);
        tokenB = jwtService.generateAccessToken(userB);
    }

    @Test
    @DisplayName("POST /api/recipes/cookbook: Yêu cầu đăng nhập, trả về 401 nếu không có Bearer token")
    void saveCookbookRecipe_unauthorized_returns401() throws Exception {
        SaveCookbookRecipeRequest req = SaveCookbookRecipeRequest.builder()
                .title("Canh nấm chay")
                .build();

        mockMvc.perform(post("/api/recipes/cookbook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/recipes/cookbook: Lưu thành công công thức vào sổ tay cá nhân")
    void saveCookbookRecipe_success_returns201() throws Exception {
        SaveCookbookRecipeRequest req = SaveCookbookRecipeRequest.builder()
                .title("Canh cải ngọt nấu nấm bào ngư")
                .description("Món canh ngọt lành thanh lọc cơ thể")
                .cookTimeMinutes(20)
                .difficulty("EASY")
                .estimatedCalories(120)
                .dietaryType("VEGAN")
                .originSource("FRIDGE_SCAN")
                .ingredients(List.of("300g Cải ngọt", "150g Nấm bào ngư", "1 miếng Đậu hũ non"))
                .instructions(List.of("Rửa sạch nguyên liệu", "Xào sơ nấm với dầu mè", "Đun sôi nước và thả cải ngọt vào", "Nêm muối tiêu vừa ăn"))
                .safetyNotes(List.of("Người dị ứng nấm cần lưu ý"))
                .sourceNotes("Nấu cho 2 người ăn, giảm một nửa muối")
                .build();

        mockMvc.perform(post("/api/recipes/cookbook")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.title").value("Canh cải ngọt nấu nấm bào ngư"))
                .andExpect(jsonPath("$.data.ingredients", hasSize(3)))
                .andExpect(jsonPath("$.data.instructions", hasSize(4)))
                .andExpect(jsonPath("$.data.sourceNotes").value("Nấu cho 2 người ăn, giảm một nửa muối"));

        assertEquals(1, customRecipeRepository.countByUserId(userA.getUserId()));
    }

    @Test
    @DisplayName("GET /api/recipes/cookbook: Lấy danh sách sổ tay và đảm bảo cách ly dữ liệu giữa người dùng")
    void getCookbookRecipes_verifiesUserIsolation() throws Exception {
        // Tạo 2 món cho User A
        CustomRecipe recipeA1 = CustomRecipe.builder()
                .userId(userA.getUserId())
                .title("Món A1: Đậu hũ sốt cà chua")
                .description("Đậu hũ non sốt cà tươi")
                .dietaryType("VEGAN")
                .build();
        CustomRecipe recipeA2 = CustomRecipe.builder()
                .userId(userA.getUserId())
                .title("Món A2: Nấm rơm kho tiêu")
                .description("Nấm rơm cay nhẹ")
                .dietaryType("VEGAN")
                .build();
        customRecipeRepository.saveAll(List.of(recipeA1, recipeA2));

        // Tạo 1 món cho User B
        CustomRecipe recipeB1 = CustomRecipe.builder()
                .userId(userB.getUserId())
                .title("Món B1: Trứng hấp nấm")
                .description("Trứng gà chay hấp nấm")
                .dietaryType("LACTO_OVO")
                .build();
        customRecipeRepository.save(recipeB1);

        // User A gọi API -> chỉ thấy 2 món của mình
        mockMvc.perform(get("/api/recipes/cookbook")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[*].title", containsInAnyOrder("Món A1: Đậu hũ sốt cà chua", "Món A2: Nấm rơm kho tiêu")));

        // User B gọi API -> chỉ thấy 1 món của mình
        mockMvc.perform(get("/api/recipes/cookbook")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("Món B1: Trứng hấp nấm"));
    }

    @Test
    @DisplayName("GET /api/recipes/cookbook: Tìm kiếm theo từ khóa keyword")
    void getCookbookRecipes_searchByKeyword() throws Exception {
        CustomRecipe r1 = CustomRecipe.builder()
                .userId(userA.getUserId())
                .title("Canh chua chay Nam Bộ")
                .description("Món canh chua thanh mát với bạc hà đậu bắp")
                .build();
        CustomRecipe r2 = CustomRecipe.builder()
                .userId(userA.getUserId())
                .title("Nấm kho quẹt chay")
                .description("Ăn kèm cơm cháy hoặc rau củ luộc")
                .build();
        customRecipeRepository.saveAll(List.of(r1, r2));

        mockMvc.perform(get("/api/recipes/cookbook")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .param("keyword", "canh chua"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("Canh chua chay Nam Bộ"));
    }

    @Test
    @DisplayName("GET /api/recipes/cookbook/{id}: Xem chi tiết và chặn truy cập món của user khác")
    void getCookbookRecipeDetail_accessControl() throws Exception {
        CustomRecipe recipeA = CustomRecipe.builder()
                .userId(userA.getUserId())
                .title("Bún riêu chay")
                .description("Bún riêu đậu hũ cà chua")
                .ingredients(List.of("Bún tươi", "Đậu hũ", "Cà chua", "Nấm"))
                .instructions(List.of("Nấu nước dùng", "Cho bún ra tô và chan nước"))
                .build();
        recipeA = customRecipeRepository.save(recipeA);

        // User A xem món của chính mình -> OK
        mockMvc.perform(get("/api/recipes/cookbook/{id}", recipeA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Bún riêu chay"))
                .andExpect(jsonPath("$.data.ingredients", hasSize(4)))
                .andExpect(jsonPath("$.data.instructions", hasSize(2)));

        // User B cố tình truy cập món của User A -> 404 COOKBOOK_RECIPE_NOT_FOUND
        mockMvc.perform(get("/api/recipes/cookbook/{id}", recipeA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2104));
    }

    @Test
    @DisplayName("PUT /api/recipes/cookbook/{id}: Cập nhật công thức và ghi chú cá nhân thành công")
    void updateCookbookRecipe_success() throws Exception {
        CustomRecipe recipeA = CustomRecipe.builder()
                .userId(userA.getUserId())
                .title("Rau muống xào tỏi chay")
                .cookTimeMinutes(10)
                .build();
        recipeA = customRecipeRepository.save(recipeA);

        UpdateCookbookRecipeRequest updateReq = UpdateCookbookRecipeRequest.builder()
                .title("Rau muống xào nấm tỏi thơm lừng")
                .cookTimeMinutes(12)
                .sourceNotes("Bí quyết: Xào trên lửa thật lớn để rau giữ màu xanh giòn")
                .build();

        mockMvc.perform(put("/api/recipes/cookbook/{id}", recipeA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Rau muống xào nấm tỏi thơm lừng"))
                .andExpect(jsonPath("$.data.cookTimeMinutes").value(12))
                .andExpect(jsonPath("$.data.sourceNotes").value("Bí quyết: Xào trên lửa thật lớn để rau giữ màu xanh giòn"));
    }

    @Test
    @DisplayName("DELETE /api/recipes/cookbook/{id}: Xóa công thức khỏi sổ tay cá nhân")
    void deleteCookbookRecipe_success() throws Exception {
        CustomRecipe recipeA = CustomRecipe.builder()
                .userId(userA.getUserId())
                .title("Món thử nghiệm cần xóa")
                .build();
        recipeA = customRecipeRepository.save(recipeA);
        UUID recipeId = recipeA.getId();

        mockMvc.perform(delete("/api/recipes/cookbook/{id}", recipeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertFalse(customRecipeRepository.existsById(recipeId));

        // Kiểm tra gọi lại chi tiết trả về 404
        mockMvc.perform(get("/api/recipes/cookbook/{id}", recipeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/ai/generate-recipe: Tự động lưu công thức vào sổ tay khi người dùng bấm Chế biến")
    void generateRecipe_autoSavesToCookbook() throws Exception {
        GenerateRecipeAiRequest req = GenerateRecipeAiRequest.builder()
                .recipeName("Canh cải ngọt nấu nấm bào ngư thanh đạm")
                .availableIngredients(List.of("Cải ngọt", "Nấm bào ngư", "Đậu phụ"))
                .contextNotes("Gia giảm ít muối")
                .autoSave(true)
                .build();

        mockMvc.perform(post("/api/ai/generate-recipe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.title").isNotEmpty())
                .andExpect(jsonPath("$.data.ingredients", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.instructions", hasSize(greaterThan(0))));

        // Kiểm tra sổ tay món ăn của User A đã có ngay công thức này
        mockMvc.perform(get("/api/recipes/cookbook")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value(containsString("Canh")));
    }
}
