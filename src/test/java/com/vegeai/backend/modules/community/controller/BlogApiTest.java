package com.vegeai.backend.modules.community.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.auth.service.*;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.user.entity.*;
import com.vegeai.backend.modules.user.repository.UserRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class BlogApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired BlogPostRepository posts;
    @Autowired JwtService jwt;
    @MockBean TokenBlacklistService blacklist;
    User member, other, admin;
    String token, otherToken, adminToken;

    @BeforeEach void setup() {
        member = user(UserRole.MEMBER);
        other = user(UserRole.MEMBER);
        admin = user(UserRole.ADMIN);
        token = jwt.generateAccessToken(member);
        otherToken = jwt.generateAccessToken(other);
        adminToken = jwt.generateAccessToken(admin);
    }
    User user(UserRole role) {
        String id = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder().username("blog-" + id).email(id + "@example.test")
                .fullName("Blog Fixture").passwordHash("test-only").role(role).status(AccountStatus.ACTIVE).build());
    }
    JsonNode call(String method, String path, String auth, Object body, int code) throws Exception {
        var builder = request(HttpMethod.valueOf(method), URI.create(path));
        if (auth != null) builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + auth);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var result = mvc.perform(builder).andExpect(status().is(code))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }
    Map<String, Object> input(String title) {
        return new HashMap<>(Map.of("title", title, "excerpt", "A plant-based kitchen story", "category", "GREEN_LIVING",
                "content", "An independently written kitchen story about vegetables and plant-based cooking. ".repeat(3),
                "coverImage", "/images/fresh-vegetables.png"));
    }
    JsonNode draft(String title) throws Exception { return call("POST", "/api/blog/my-posts", token, input(title), 201).path("data"); }
    String id(JsonNode post) { return post.path("postId").asText(); }
    long version(JsonNode post) { return post.path("version").asLong(); }
    String own(JsonNode post) { return "/api/blog/my-posts/" + id(post); }
    JsonNode submit(JsonNode post) throws Exception { return call("POST", own(post) + "/submit", token, Map.of("version", version(post)), 200).path("data"); }
    JsonNode review(JsonNode post, String decision, String reason, int status) throws Exception {
        return call("POST", "/api/admin/blog/posts/" + id(post) + "/review", adminToken,
                Map.of("version", version(post), "decision", decision, "reason", reason), status).path("data");
    }

    @Test void publicRoutesWorkWithoutLoginAndPrivateRoutesStayProtected() throws Exception {
        call("GET", "/api/blog/posts?page=0&size=9", null, null, 200);
        call("GET", "/api/blog/posts?category=GREEN_LIVING", null, null, 200);
        call("GET", "/api/blog/posts/" + UUID.randomUUID(), null, null, 404);
        call("GET", "/api/blog/my-posts", null, null, 401);
        call("POST", "/api/blog/my-posts", null, input("Guest post"), 401);
        call("GET", "/api/admin/blog/posts", null, null, 401);
        call("GET", "/api/admin/blog/posts", token, null, 403);
        assertThat(call("GET", "/api/blog/my-posts", token, null, 200).path("data").path("items").size()).isZero();
    }

    @Test void cannotForgePublicationAuthorOrReadAnotherAuthorsDraft() throws Exception {
        var input = input("Private draft");
        input.put("status", "PUBLISHED"); input.put("authorId", other.getUserId()); input.put("moderationReason", "forged");
        JsonNode post = call("POST", "/api/blog/my-posts", token, input, 201).path("data");
        assertThat(post.path("status").asText()).isEqualTo("DRAFT");
        assertThat(post.path("authorId").asText()).isEqualTo(member.getUserId().toString());
        call("GET", "/api/blog/posts/" + id(post), null, null, 404);
        call("GET", own(post), otherToken, null, 404);
        call("PUT", own(post), otherToken, input, 404);
        call("POST", own(post) + "/submit", otherToken, Map.of("version", 0), 404);
        call("DELETE", own(post) + "?version=0", otherToken, null, 404);
        assertThat(call("GET", "/api/blog/my-posts", otherToken, null, 200).path("data").path("totalElements").asInt()).isZero();
        assertThat(call("GET", "/api/blog/posts", null, null, 200).path("data").path("totalElements").asInt()).isZero();
    }

    @Test void draftSubmissionReviewAndPublicDetailsUseFrontendContract() throws Exception {
        JsonNode post = submit(draft("A green kitchen story"));
        assertThat(post.path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(post.path("submittedAt").asText()).isNotBlank();
        call("GET", "/api/blog/posts/" + id(post), null, null, 404);
        assertThat(call("GET", "/api/admin/blog/posts?status=PENDING_REVIEW", adminToken, null, 200).path("data").path("totalElements").asInt()).isEqualTo(1);
        post = review(post, "APPROVE", "", 200);
        assertThat(post.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(post.path("readingMinutes").asInt()).isPositive();
        assertThat(post.path("publishedAt").asText()).isNotBlank();
        JsonNode publicPost = call("GET", "/api/blog/posts/" + id(post), null, null, 200).path("data");
        assertThat(publicPost.path("content").asText()).contains("vegetables");
        for (String field : List.of("moderationReason", "reviewerName", "reviewedAt", "email", "passwordHash")) {
            assertThat(publicPost.has(field)).isFalse();
        }
        call("PUT", own(post), token, withVersion(input("Updated title"), version(post)), 409);
        call("DELETE", own(post) + "?version=" + version(post), token, null, 409);
        call("POST", own(post) + "/withdraw", token, Map.of("version", version(post)), 409);
    }

    Map<String, Object> withVersion(Map<String, Object> body, long version) { body.put("version", version); return body; }

    @Test void rejectionRequiresReasonAndAuthorCanReviseAndResubmit() throws Exception {
        JsonNode post = submit(draft("A story for review"));
        review(post, "REJECT", "   ", 400);
        post = review(post, "REJECT", "Please add the preparation steps.", 200);
        assertThat(call("GET", own(post), token, null, 200).path("data").path("moderationReason").asText()).contains("preparation");
        call("GET", own(post), otherToken, null, 404);
        call("GET", "/api/blog/posts/" + id(post), null, null, 404);
        post = call("PUT", own(post), token, withVersion(input("Revised kitchen story"), version(post)), 200).path("data");
        post = submit(post);
        assertThat(post.has("moderationReason")).isFalse();
        post = review(post, "APPROVE", "", 200);
        assertThat(post.path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test void pendingPostCannotBeEditedOrDeletedUntilWithdrawn() throws Exception {
        JsonNode post = submit(draft("Pending story"));
        call("PUT", own(post), token, withVersion(input("Changed while pending"), version(post)), 409);
        call("DELETE", own(post) + "?version=" + version(post), token, null, 409);
        post = call("POST", own(post) + "/withdraw", token, Map.of("version", version(post)), 200).path("data");
        assertThat(post.path("status").asText()).isEqualTo("DRAFT");
        call("DELETE", own(post) + "?version=" + version(post), token, null, 200);
        call("GET", own(post), token, null, 404);
    }

    @Test void staleSaveReviewWithdrawAndDeleteCannotOverwriteCurrentVersion() throws Exception {
        JsonNode post = draft("Original story");
        JsonNode edited = call("PUT", own(post), token, withVersion(input("Changed story"), version(post)), 200).path("data");
        assertThat(version(edited)).isGreaterThan(version(post));
        call("PUT", own(post), token, withVersion(input("Stale story"), version(post)), 409);
        call("DELETE", own(post) + "?version=" + version(post), token, null, 409);
        JsonNode pending = submit(edited);
        review(edited, "APPROVE", "", 409);
        call("POST", own(pending) + "/withdraw", token, Map.of("version", version(edited)), 409);
        JsonNode approved = review(pending, "APPROVE", "", 200);
        review(pending, "REJECT", "Stale decision", 409);
        assertThat(call("GET", own(approved), token, null, 200).path("data").path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test void missingRevisionsInvalidInputsAndUnsafeImagesAreRejected() throws Exception {
        call("POST", "/api/blog/my-posts", token, input("   "), 400);
        for (String cover : List.of("javascript:alert(1)", "https://user:password@example.test/image.png", "//evil.test/image.png")) {
            var input = input("Invalid cover"); input.put("coverImage", cover);
            call("POST", "/api/blog/my-posts", token, input, 400);
        }
        JsonNode post = draft("A valid draft");
        call("PUT", own(post), token, input("No revision"), 400);
        call("POST", own(post) + "/submit", token, Map.of(), 400);
        call("DELETE", own(post), token, null, 400);
        call("GET", "/api/blog/posts?category=UNKNOWN", null, null, 400);
        call("GET", "/api/blog/posts?page=-1", null, null, 400);
        call("GET", "/api/blog/posts?size=51", null, null, 400);
        call("GET", "/api/blog/posts?keyword=" + "x".repeat(201), null, null, 400);
    }

    @Test void shortDraftCanBeSavedButCannotBeSubmitted() throws Exception {
        var input = input("Hi"); input.put("content", "Short draft");
        JsonNode post = call("POST", "/api/blog/my-posts", token, input, 201).path("data");
        call("POST", own(post) + "/submit", token, Map.of("version", version(post)), 400);
    }

    @Test void filtersPaginationAndLiteralWildcardSearchReturnOnlyPublishedPosts() throws Exception {
        review(submit(draft("Green story 100%")), "APPROVE", "", 200);
        review(submit(draft("Another green story")), "APPROVE", "", 200);
        draft("Private green story");
        JsonNode list = call("GET", "/api/blog/posts?category=GREEN_LIVING&size=1&page=0", null, null, 200).path("data");
        assertThat(list.path("totalElements").asInt()).isEqualTo(2);
        assertThat(list.path("totalPages").asInt()).isEqualTo(2);
        assertThat(list.path("items").get(0).has("content")).isFalse();
        assertThat(call("GET", "/api/blog/posts?keyword=%25", null, null, 200).path("data").path("totalElements").asInt()).isEqualTo(1);
        assertThat(call("GET", "/api/blog/posts?category=KITCHEN", null, null, 200).path("data").path("totalElements").asInt()).isZero();
        assertThat(call("GET", "/api/blog/my-posts?status=DRAFT", token, null, 200).path("data").path("totalElements").asInt()).isEqualTo(1);
    }

    @Test void memberCannotReviewAndDraftCannotBeApprovedWithoutSubmission() throws Exception {
        JsonNode post = draft("Review permission");
        call("GET", "/api/admin/blog/posts/" + id(post), token, null, 403);
        call("POST", "/api/admin/blog/posts/" + id(post) + "/review", token, Map.of("version", 0, "decision", "APPROVE"), 403);
        review(post, "APPROVE", "", 409);
    }

    @Test void lockedAccountAndDemotedAdminAreRecheckedDespiteExistingTokens() throws Exception {
        member.setStatus(AccountStatus.LOCKED); users.saveAndFlush(member);
        call("GET", "/api/blog/my-posts", token, null, 403);
        admin.setRole(UserRole.MEMBER); users.saveAndFlush(admin);
        call("GET", "/api/admin/blog/posts", adminToken, null, 403);
    }
}
