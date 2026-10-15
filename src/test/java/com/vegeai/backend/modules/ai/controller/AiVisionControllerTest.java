package com.vegeai.backend.modules.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.auth.service.JwtService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.vegeai.backend.modules.ai.dto.GenerateRecipeAiRequest;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.test.context.TestPropertySource;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.gemini.api-key=", "GEMINI_API_KEY="})
class AiVisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User memberUser;
    private String memberToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        memberUser = User.builder()
                .username("fridgeuser")
                .email("fridgeuser@vegeai.com")
                .passwordHash(passwordEncoder.encode("hashed_pass"))
                .fullName("Thanh Tai Tester")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .allergies("đậu phụ, nấm")
                .dietaryPreferences("VEGAN")
                .build();
        userRepository.save(memberUser);

        memberToken = jwtService.generateAccessToken(memberUser);
    }

    @Test
    @DisplayName("FR-VIS-01/02: Guest quét ảnh tủ lạnh thành công -> trả về danh sách nguyên liệu & độ tươi")
    void scanFridge_Success_Guest() throws Exception {
        byte[] dummyImageBytes = "mock-jpeg-binary-image-data-for-fridge-scanning".getBytes();
        MockMultipartFile imageFile = new MockMultipartFile(
                "image",
                "fridge.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                dummyImageBytes
        );

        mockMvc.perform(multipart("/api/ai/fridge-scan")
                        .file(imageFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.ingredients", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.ingredients[0].name", notNullValue()))
                .andExpect(jsonPath("$.data.ingredients[0].freshness", notNullValue()))
                .andExpect(jsonPath("$.data.ingredients[0].isVegetarian", is(true)))
                .andExpect(jsonPath("$.data.suggestedRecipes", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("FR-VIS-03: Member quét ảnh có kèm Token -> Kiểm tra chéo dị ứng thành công")
    void scanFridge_Success_WithMemberAllergyWarning() throws Exception {
        byte[] dummyImageBytes = "mock-png-binary-image-data".getBytes();
        MockMultipartFile imageFile = new MockMultipartFile(
                "image",
                "vegetables.png",
                MediaType.IMAGE_PNG_VALUE,
                dummyImageBytes
        );

        mockMvc.perform(multipart("/api/ai/fridge-scan")
                        .file(imageFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.ingredients", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.warnings", notNullValue()));
    }

    @Test
    @DisplayName("FR-VIS-01: Gửi file ảnh rỗng -> 400 Bad Request (IMAGE_EMPTY)")
    void scanFridge_EmptyImage_ReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "image",
                "empty.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/ai/fridge-scan")
                        .file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(1701)));
    }

    @Test
    @DisplayName("FR-VIS-01: Gửi định dạng không hỗ trợ (PDF/TXT) -> 415 Unsupported Media Type")
    void scanFridge_UnsupportedFormat_ReturnsUnsupportedMediaType() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "image",
                "document.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "some-pdf-content".getBytes()
        );

        mockMvc.perform(multipart("/api/ai/fridge-scan")
                        .file(textFile))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(1703)));
    }

    @Test
    @DisplayName("FEAT-COOK: Member profile complete -> POST /api/ai/generate-recipe succeeds (200 OK)")
    void generateRecipe_Success_WhenProfileCompleted() throws Exception {
        GenerateRecipeAiRequest req = GenerateRecipeAiRequest.builder()
                .recipeName("Canh cải ngọt nấu nấm bào ngư thanh đạm")
                .availableIngredients(List.of("Cải ngọt", "Nấm bào ngư"))
                .contextNotes("Nấu thanh đạm, ít dầu")
                .build();

        mockMvc.perform(post("/api/ai/generate-recipe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.title", is("Canh cải ngọt nấu nấm bào ngư thanh đạm")))
                .andExpect(jsonPath("$.data.dietaryType", is("VEGAN")))
                .andExpect(jsonPath("$.data.ingredients", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.instructions", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.requiresProfileSetup", is(false)));
    }

    @Test
    @DisplayName("FEAT-COOK: Member profile INCOMPLETE -> POST /api/ai/generate-recipe returns 403 PROFILE_INCOMPLETE")
    void generateRecipe_IncompleteProfile_ThrowsProfileIncomplete() throws Exception {
        User incompleteUser = User.builder()
                .username("incomplete_user")
                .email("incomplete@vegeai.com")
                .passwordHash(passwordEncoder.encode("hashed_pass"))
                .fullName("Incomplete User")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .dietaryPreferences(null) // Profile not completed!
                .build();
        userRepository.save(incompleteUser);
        String incompleteToken = jwtService.generateAccessToken(incompleteUser);

        GenerateRecipeAiRequest req = GenerateRecipeAiRequest.builder()
                .recipeName("Đậu phụ sốt cà chua")
                .build();

        mockMvc.perform(post("/api/ai/generate-recipe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + incompleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(2004)));
    }

    @Test
    @DisplayName("FEAT-COOK: Guest calling POST /api/ai/generate-recipe returns 401 UNAUTHORIZED")
    void generateRecipe_Guest_ReturnsUnauthorized() throws Exception {
        GenerateRecipeAiRequest req = GenerateRecipeAiRequest.builder()
                .recipeName("Salad bơ")
                .build();

        mockMvc.perform(post("/api/ai/generate-recipe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(1100)));
    }
}
