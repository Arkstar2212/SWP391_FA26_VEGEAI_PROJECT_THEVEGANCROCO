package com.vegeai.backend.modules.admin.controller;

import com.fasterxml.jackson.databind.*;
import com.vegeai.backend.modules.auth.service.*;
import com.vegeai.backend.modules.community.entity.*;
import com.vegeai.backend.modules.community.repository.VideoRepository;
import com.vegeai.backend.modules.user.entity.*;
import com.vegeai.backend.modules.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AdminRoleBoundaryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired VideoRepository videos;
    @Autowired EntityManager em;
    @Autowired JwtService jwt;
    @MockBean TokenBlacklistService blacklist;
    User admin, member;
    String adminToken, memberToken;

    @BeforeEach void setup() {
        admin = user(UserRole.ADMIN); member = user(UserRole.MEMBER);
        adminToken = jwt.generateAccessToken(admin); memberToken = jwt.generateAccessToken(member);
    }
    User user(UserRole role) {
        String id = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder().username("boundary-" + id).email(id + "@example.test")
                .fullName("Role fixture").passwordHash("test-only").role(role).status(AccountStatus.ACTIVE).build());
    }
    JsonNode call(String method, String path, String token, Object body, int code) throws Exception {
        var builder = request(HttpMethod.valueOf(method), URI.create(path));
        if (token != null) builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var result = mvc.perform(builder).andExpect(status().is(code)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }
    @Test void adminCannotUsePersonalApisButCanReadPublicContentAndManageAccounts() throws Exception {
        for (String path : List.of("/api/fridge", "/api/nutrition/profile", "/api/ai/chat/history", "/api/recipes/cookbook", "/api/recipes/fridge-match")) {
            call("GET", path, adminToken, null, 403);
        }
        call("POST", "/api/ai/chat", adminToken, Map.of("message", "Hello"), 403);
        call("GET", "/api/admin/users", adminToken, null, 200);
        call("GET", "/api/videos", adminToken, null, 200);
        call("GET", "/api/questions", adminToken, null, 200);
        call("POST", "/api/questions", adminToken, Map.of("content", "Not permitted for admins"), 403);
        call("GET", "/api/questions/mine", adminToken, null, 403);
        call("GET", "/api/recipes/fridge-match", memberToken, null, 200);
        call("GET", "/api/videos", memberToken, null, 200);
    }
    @Test void managementRequiresCurrentActiveAdminEvenWithOldToken() throws Exception {
        call("GET", "/api/admin/users", memberToken, null, 403);
        admin.setStatus(AccountStatus.LOCKED); users.saveAndFlush(admin);
        call("GET", "/api/admin/users", adminToken, null, 403);
        admin.setStatus(AccountStatus.ACTIVE); admin.setRole(UserRole.MEMBER); users.saveAndFlush(admin);
        call("GET", "/api/admin/users", adminToken, null, 403);
        member.setRole(UserRole.ADMIN); users.saveAndFlush(member);
        call("GET", "/api/recipes/fridge-match", memberToken, null, 403);
    }
    @Test void guestHasOnlyRecipeBlogQuestionAndPlaceBusinessAccess() throws Exception {
        for(String path:List.of("/api/recipes", "/api/recipes/tags", "/api/blog/posts", "/api/questions", "/api/places/categories"))
            call("GET",path,null,null,200);
        call("POST","/api/recipes/filter",null,Map.of("page",0,"size",9),200);
        call("GET", "/api/videos", null, null, 200);
        for(String path:List.of("/api/videos/"+UUID.randomUUID(), "/api/videos/"+UUID.randomUUID()+"/comments"))
            call("GET",path,null,null,404);
        for(String path:List.of("/api/nutrition/profile", "/api/meal-plans", "/api/fridge/items",
                "/api/users/me", "/api/recipes/cookbook", "/api/recipes/fridge-match", "/api/admin/users"))
            call("GET",path,null,null,401);
        call("GET", "/api/nutrition/options", null, null, 200);
        call("POST", "/api/nutrition/calculate", null, Map.of("message","invalid public payload"), 400);
        for(String path:List.of("/api/meal-plans/generate"))
            call("POST",path,null,Map.of("message","Guest is not allowed"),401);
    }
    @Test void managementResponsesDoNotExposePrivateHealthData() throws Exception {
        member.setHeightCm(170.0); member.setWeightKg(65.0);
        users.saveAndFlush(member);
        String target = "/api/admin/users/" + member.getUserId();
        List<JsonNode> responses = List.of(
                call("GET", target, adminToken, null, 200).path("data"),
                call("GET", "/api/admin/users?search=" + member.getUsername(), adminToken, null, 200).path("data").path("items").get(0),
                call("PATCH", target + "/status", adminToken, Map.of("status", "ACTIVE"), 200).path("data"),
                call("PATCH", target + "/role", adminToken, Map.of("role", "MEMBER"), 200).path("data"));
        for (JsonNode response : responses) {
            assertThat(response.path("username").asText()).isEqualTo(member.getUsername());
            for (String field : List.of("heightCm", "weightKg", "bmi", "allergies", "dietaryPreference", "dateOfBirth", "targetCalories", "passwordHash")) {
                assertThat(response.has(field)).as(field).isFalse();
            }
        }
        assertThat(call("GET", "/api/users/me", memberToken, null, 200).path("data").path("heightCm").asDouble()).isEqualTo(170.0);
        for (JsonNode log : call("GET", "/api/admin/ai/audit-logs", adminToken, null, 200).path("data")) {
            assertThat(log.path("promptSummary").asText()).isIn("", "null");
        }
    }
    CommunityVideo video(VideoStatus status) {
        var video = new CommunityVideo(); video.setTitle("Fixture " + status); video.setStatus(status);
        video.setVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"); video.setCreatedBy(admin);
        return videos.saveAndFlush(video);
    }
    Map<String, Object> recipe(List<UUID> videoIds) {
        return new HashMap<>(Map.of("title", "Fixture recipe " + UUID.randomUUID(), "status", "PUBLISHED",
                "ingredients", List.of(Map.of("name", "Tofu", "amount", 100, "unit", "g")),
                "instructions", List.of("Steam the tofu."), "videoIds", videoIds));
    }
    @Test void recipeLinksKeepDraftVideosPrivateAndAllowUpdateAndUnlinkOnDeletion() throws Exception {
        var published = video(VideoStatus.PUBLISHED); var draft = video(VideoStatus.DRAFT);
        JsonNode created = call("POST", "/api/admin/recipes", adminToken, recipe(List.of(published.getId(), draft.getId())), 201).path("data");
        String id = created.path("recipeId").asText();
        assertThat(created.path("videos").size()).isEqualTo(2);
        assertThat(call("GET", "/api/recipes/" + id, null, null, 200).path("data").path("videos").size()).isEqualTo(1);
        assertThat(call("GET", "/api/recipes/slug/" + created.path("slug").asText(), null, null, 200).path("data").path("videos").size()).isEqualTo(1);
        assertThat(call("GET", "/api/recipes/" + id, memberToken, null, 200).path("data").path("videos").size()).isEqualTo(1);
        assertThat(call("GET", "/api/admin/recipes/" + id, adminToken, null, 200).path("data").path("videos").size()).isEqualTo(2);
        call("PUT", "/api/admin/recipes/" + id, adminToken, Map.of("description", "Keep videos"), 200);
        assertThat(call("GET", "/api/admin/recipes/" + id, adminToken, null, 200).path("data").path("videos").size()).isEqualTo(2);
        call("DELETE", "/api/admin/videos/" + published.getId() + "?version=" + published.getVersion(), adminToken, null, 200);
        em.flush(); em.clear();
        assertThat(call("GET", "/api/recipes/" + id, null, null, 200).path("data").path("videos").size()).isZero();
        call("PUT", "/api/admin/recipes/" + id, adminToken, Map.of("videoIds", List.of()), 200);
        assertThat(call("GET", "/api/admin/recipes/" + id, adminToken, null, 200).path("data").path("videos").size()).isZero();
    }
    @Test void recipeRejectsBadLinksAndKeepsDraftRecipePrivate() throws Exception {
        call("POST", "/api/admin/recipes", adminToken, recipe(List.of(UUID.randomUUID())), 404);
        call("POST", "/api/admin/recipes", adminToken, recipe(Collections.nCopies(11, UUID.randomUUID())), 400);
        var body = recipe(List.of()); body.put("status", "DRAFT");
        JsonNode draft = call("POST", "/api/admin/recipes", adminToken, body, 201).path("data");
        call("GET", "/api/recipes/" + draft.path("recipeId").asText(), null, null, 404);
        call("GET", "/api/admin/recipes/" + draft.path("recipeId").asText(), adminToken, null, 200);
    }
    @Test void adminRecipeListIncludesAllStatusesAndSupportsFiltering() throws Exception {
        for (String status : List.of("PUBLISHED", "DRAFT", "ARCHIVED")) {
            var body = recipe(List.of());
            body.put("title", "Admin catalog " + status);
            body.put("status", status);
            call("POST", "/api/admin/recipes", adminToken, body, 201);
        }
        JsonNode all = call("GET", "/api/admin/recipes?keyword=Admin%20catalog&page=0&size=10", adminToken, null, 200).path("data");
        assertThat(all.path("totalElements").asInt()).isEqualTo(3);
        assertThat(all.path("items").findValuesAsText("status"))
                .containsExactlyInAnyOrder("PUBLISHED", "DRAFT", "ARCHIVED");
        JsonNode drafts = call("GET", "/api/admin/recipes?status=DRAFT", adminToken, null, 200).path("data");
        assertThat(drafts.path("totalElements").asInt()).isEqualTo(1);
        assertThat(drafts.path("items").get(0).path("status").asText()).isEqualTo("DRAFT");
        call("GET", "/api/admin/recipes", memberToken, null, 403);
    }
}
