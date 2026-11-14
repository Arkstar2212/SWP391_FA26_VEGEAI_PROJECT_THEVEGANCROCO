package com.vegeai.backend.modules.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.admin.repository.AiMonitoringRepository;
import com.vegeai.backend.modules.ai.dto.BlogInsightAiRequest;
import com.vegeai.backend.modules.auth.service.JwtService;
import com.vegeai.backend.modules.community.entity.BlogCategory;
import com.vegeai.backend.modules.community.entity.BlogPost;
import com.vegeai.backend.modules.community.entity.BlogStatus;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import com.vegeai.backend.support.GeminiIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlogInsightAiControllerTest extends GeminiIntegrationTestSupport {

    @MockBean
    private com.vegeai.backend.modules.community.service.CommunityDataInitializer communityDataInitializer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlogPostRepository blogPostRepository;

    @Autowired
    private AiMonitoringRepository aiMonitoringRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User memberUser;
    private User adminUser;
    private String memberToken;
    private String adminToken;
    private BlogPost publishedPost;
    private BlogPost otherDraftPost;

    @BeforeEach
    void setUp() {
        cleanUp();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        memberUser = userRepository.save(User.builder()
                .username("bloginsight_member_" + suffix)
                .email("bloginsight_member_" + suffix + "@vegeai.test")
                .passwordHash(passwordEncoder.encode("SecretPass123!"))
                .fullName("Nguyen Minh Chay")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .dietaryPreferences("VEGAN")
                .allergies("đậu phộng")
                .dailyCalorieTarget(1900)
                .build());

        adminUser = userRepository.save(User.builder()
                .username("bloginsight_admin_" + suffix)
                .email("bloginsight_admin_" + suffix + "@vegeai.test")
                .passwordHash(passwordEncoder.encode("SecretPass123!"))
                .fullName("Admin Reviewer")
                .role(UserRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());

        memberToken = jwtService.generateAccessToken(memberUser);
        adminToken = jwtService.generateAccessToken(adminUser);

        publishedPost = new BlogPost();
        publishedPost.setAuthor(adminUser);
        publishedPost.setTitle("Hiểu đúng về vitamin B12 và sắt thực vật");
        publishedPost.setExcerpt("Cách kết hợp rau xanh và đậu phộng rang trong chế độ ăn chay.");
        publishedPost.setContent("Sắt non-heme hấp thu tốt hơn khi dùng kèm vitamin C. Lưu ý một số món dùng đậu phộng giã nhỏ.");
        publishedPost.setCategory(BlogCategory.NUTRITION);
        publishedPost.setStatus(BlogStatus.PUBLISHED);
        publishedPost = blogPostRepository.save(publishedPost);

        otherDraftPost = new BlogPost();
        otherDraftPost.setAuthor(adminUser);
        otherDraftPost.setTitle("Bản nháp riêng của tác giả khác");
        otherDraftPost.setExcerpt("Chưa công khai");
        otherDraftPost.setContent("Nội dung bản nháp chưa duyệt.");
        otherDraftPost.setCategory(BlogCategory.GREEN_LIVING);
        otherDraftPost.setStatus(BlogStatus.DRAFT);
        otherDraftPost = blogPostRepository.save(otherDraftPost);
    }

    @AfterEach
    void cleanUp() {
        aiMonitoringRepository.deleteAll();
        blogPostRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Guest cannot call /api/ai/blog-insight (401 UNAUTHORIZED)")
    void analyzeBlogInsight_GuestRejected() throws Exception {
        mockMvc.perform(post("/api/ai/blog-insight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BlogInsightAiRequest(publishedPost.getId()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(1100)));
    }

    @Test
    @DisplayName("Admin is blocked from personal AI blog insight endpoint (403 FORBIDDEN)")
    void analyzeBlogInsight_AdminBlockedByRoleBoundary() throws Exception {
        mockMvc.perform(post("/api/ai/blog-insight")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BlogInsightAiRequest(publishedPost.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("Member cannot probe unpublished draft belonging to another author (404 BLOG_POST_NOT_FOUND)")
    void analyzeBlogInsight_UnpublishedForeignPostRejected() throws Exception {
        mockMvc.perform(post("/api/ai/blog-insight")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BlogInsightAiRequest(otherDraftPost.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(2201)));
    }

    @Test
    @DisplayName("Member receives structured Gemini insight with allergy alert and AI audit telemetry")
    void analyzeBlogInsight_MemberSuccessWithAllergyAndAudit() throws Exception {
        mockMvc.perform(post("/api/ai/blog-insight")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BlogInsightAiRequest(publishedPost.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.postId", is(publishedPost.getId().toString())))
                .andExpect(jsonPath("$.data.source", is("AI")))
                .andExpect(jsonPath("$.data.model", is("test-model")))
                .andExpect(jsonPath("$.data.categoryBadge", containsString("Kiến thức Dinh dưỡng")))
                .andExpect(jsonPath("$.data.keyTakeaways", hasSize(2)))
                .andExpect(jsonPath("$.data.actionableSteps", hasSize(2)))
                .andExpect(jsonPath("$.data.personalizedNote", containsString("đậu phộng")))
                .andExpect(jsonPath("$.data.nutritionSafetyDisclaimer", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.suggestedFollowUpPrompt", not(blankOrNullString())));

        assertThat(lastRequest.path("systemInstruction").toString()).contains("<blog_post>");
        assertThat(aiMonitoringRepository.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getOperation()).isEqualTo("BLOG_INSIGHT");
                    assertThat(entry.getStatus()).isEqualTo("SUCCESS");
                    assertThat(entry.getUserType()).isEqualTo("MEMBER");
                });
    }

    @Test
    @DisplayName("Incomplete structured JSON from Gemini returns 502 AI_RESPONSE_INVALID instead of fake fallback")
    void analyzeBlogInsight_IncompleteJsonRejected() throws Exception {
        BlogPost badPost = new BlogPost();
        badPost.setAuthor(adminUser);
        badPost.setTitle("fixture:blog-incomplete");
        badPost.setExcerpt("Test incomplete AI JSON");
        badPost.setContent("Nội dung test phản hồi AI thiếu mảng takeaways.");
        badPost.setCategory(BlogCategory.NUTRITION);
        badPost.setStatus(BlogStatus.PUBLISHED);
        badPost = blogPostRepository.save(badPost);

        mockMvc.perform(post("/api/ai/blog-insight")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BlogInsightAiRequest(badPost.getId()))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is(1710)));
    }
}
