package com.vegeai.backend.modules.community.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.auth.service.*;
import com.vegeai.backend.modules.community.repository.*;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CommunityApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired QuestionRepository questions;
    @Autowired VideoRepository videos;
    @Autowired VideoCommentRepository comments;
    @Autowired VideoFavoriteRepository favorites;
    @MockBean TokenBlacklistService blacklist;
    User member, other, admin;
    String memberToken, otherToken, adminToken;

    @BeforeEach void setup() {
        member = user(UserRole.MEMBER);
        other = user(UserRole.MEMBER);
        admin = user(UserRole.ADMIN);
        memberToken = jwt.generateAccessToken(member);
        otherToken = jwt.generateAccessToken(other);
        adminToken = jwt.generateAccessToken(admin);
    }

    User user(UserRole role) {
        String suffix = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder().username("community-" + suffix).email(suffix + "@example.test")
                .fullName("Community Fixture").passwordHash(UUID.randomUUID().toString())
                .role(role).status(AccountStatus.ACTIVE).build());
    }

    JsonNode call(String method, String path, String token, Object body, int status) throws Exception {
        var builder = request(HttpMethod.valueOf(method), java.net.URI.create(path));
        if (token != null) builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var result = mvc.perform(builder).andExpect(status().is(status))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    JsonNode question(String content) throws Exception {
        return call("POST", "/api/questions", memberToken, Map.of("content", content), 201).path("data");
    }

    JsonNode approve(JsonNode question) throws Exception {
        return call("PUT", "/api/admin/questions/" + question.path("id").asText() + "/moderation", adminToken,
                Map.of("decision", "APPROVE", "version", question.path("version").asLong()), 200).path("data");
    }

    Map<String, Object> videoBody(String title, String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title); body.put("description", "Nau rau va dau phu");
        body.put("videoUrl", "https://www.youtube.com/watch?v=fixture12345");
        body.put("thumbnailUrl", "https://example.test/vegetables.jpg"); body.put("category", "MON_CHAY");
        body.put("durationSeconds", 180); body.put("status", status);
        return body;
    }

    JsonNode video(String title, String status) throws Exception {
        return call("POST", "/api/admin/videos", adminToken, videoBody(title, status), 201).path("data");
    }

    String id(JsonNode value) { return value.path("id").asText(); }
    long version(JsonNode value) { return value.path("version").asLong(); }

    @Test void guestCanBrowseButCannotWriteReadPrivateListsOrAdmin() throws Exception {
        call("GET", "/api/questions", null, null, 200);
        call("GET", "/api/videos", null, null, 200);
        for (String path : List.of("/api/questions/mine", "/api/videos/favorites", "/api/admin/questions", "/api/admin/videos")) {
            call("GET", path, null, null, 401);
        }
        call("POST", "/api/questions", null, Map.of("content", "Question from guest"), 401);
        call("POST", "/api/admin/videos", null, videoBody("Guest video", "PUBLISHED"), 401);
    }

    @Test void adminManagesQuestionsButCannotCreateOrAccessMemberQuestionLists() throws Exception {
        long before = questions.count();
        call("POST", "/api/questions", adminToken, Map.of("content", "Admin must not create questions"), 403);
        call("GET", "/api/questions/mine", adminToken, null, 403);
        call("GET", "/api/questions/mine/" + UUID.randomUUID(), adminToken, null, 403);
        assertThat(questions.count()).isEqualTo(before);
        call("GET", "/api/admin/questions", adminToken, null, 200);
        JsonNode q = approve(question("A member asks and the admin reviews"));
        call("PUT", "/api/admin/questions/" + id(q) + "/answer", adminToken,
                Map.of("content", "An official admin answer", "version", version(q)), 200);
    }

    @Test void promotedMemberCannotCreateQuestionsWithAnOldMemberToken() throws Exception {
        member.setRole(UserRole.ADMIN);
        users.saveAndFlush(member);
        call("POST", "/api/questions", memberToken, Map.of("content", "Must check current database role"), 403);
        call("GET", "/api/questions/mine", memberToken, null, 403);
    }

    @Test void submissionCannotForgeApprovalOrAuthorAndPendingIsPrivate() throws Exception {
        JsonNode q = call("POST", "/api/questions", memberToken, Map.of("content", "How to cook vegetables?", "status", "APPROVED",
                "authorId", other.getUserId(), "answer", "forged"), 201).path("data");
        assertThat(q.path("status").asText()).isEqualTo("PENDING");
        assertThat(questions.findById(UUID.fromString(id(q))).orElseThrow().getAuthor().getUserId()).isEqualTo(member.getUserId());
        assertThat(call("GET", "/api/questions", null, null, 200).path("data").path("totalElements").asInt()).isZero();
        call("GET", "/api/questions/" + id(q), null, null, 404);
        call("GET", "/api/questions/mine/" + id(q), otherToken, null, 404);
        assertThat(call("GET", "/api/questions/mine", memberToken, null, 200).path("data").path("totalElements").asInt()).isEqualTo(1);
    }

    @Test void answerBeforeApprovalStaysPrivateThenBecomesPublicWithNoPrivateReviewMetadata() throws Exception {
        JsonNode q = question("How long should I cook beans?");
        q = call("PUT", "/api/admin/questions/" + id(q) + "/answer", adminToken,
                Map.of("content", "Soak first and cook until tender.", "version", version(q)), 200).path("data");
        assertThat(version(q)).isEqualTo(1);
        call("GET", "/api/questions/" + id(q), null, null, 404);
        q = approve(q);
        JsonNode publicQuestion = call("GET", "/api/questions/" + id(q), null, null, 200).path("data");
        assertThat(publicQuestion.path("answer").asText()).contains("Soak first");
        for (String field : List.of("email", "passwordHash", "moderationReason", "reviewedBy", "reviewedAt")) assertThat(publicQuestion.has(field)).isFalse();
        assertThat(call("GET", "/api/questions?answered=true", null, null, 200).path("data").path("totalElements").asInt()).isEqualTo(1);
        assertThat(call("GET", "/api/questions?answered=false", null, null, 200).path("data").path("totalElements").asInt()).isZero();
    }

    @Test void rejectionRequiresReasonAndOnlyAuthorAndAdminCanReadIt() throws Exception {
        JsonNode q = question("Question to be reviewed");
        String path = "/api/admin/questions/" + id(q) + "/moderation";
        call("PUT", path, adminToken, Map.of("decision", "REJECT", "version", version(q)), 400);
        JsonNode rejected = call("PUT", path, adminToken, Map.of("decision", "REJECT", "reason", "Private moderation note", "version", version(q)), 200).path("data");
        assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        assertThat(call("GET", "/api/questions/mine/" + id(q), memberToken, null, 200).path("data").path("moderationReason").asText()).contains("Private moderation");
        call("GET", "/api/questions/mine/" + id(q), otherToken, null, 404);
        call("GET", "/api/questions/" + id(q), null, null, 404);
        assertThat(call("GET", "/api/admin/questions?status=REJECTED", adminToken, null, 200).path("data").path("totalElements").asInt()).isEqualTo(1);
    }

    @Test void memberCannotModerateAnswerOrDeleteQuestions() throws Exception {
        JsonNode q = question("Question for admin only");
        String path = "/api/admin/questions/" + id(q);
        call("GET", "/api/admin/questions", memberToken, null, 403);
        call("PUT", path + "/answer", memberToken, Map.of("content", "Wrong actor", "version", version(q)), 403);
        call("PUT", path + "/moderation", memberToken, Map.of("decision", "APPROVE", "version", version(q)), 403);
        call("DELETE", path + "?version=" + version(q), memberToken, null, 403);
    }

    @Test void staleQuestionWritesConflictAndAdminDeletePersists() throws Exception {
        JsonNode initial = question("Question with revision control");
        JsonNode approved = approve(initial);
        String path = "/api/admin/questions/" + id(initial);
        call("PUT", path + "/answer", adminToken, Map.of("content", "Stale answer", "version", version(initial)), 409);
        call("PUT", path + "/answer", adminToken, Map.of("content", "No revision"), 400);
        call("DELETE", path + "?version=" + version(initial), adminToken, null, 409);
        call("DELETE", path + "?version=" + version(approved), adminToken, null, 200);
        assertThat(questions.existsById(UUID.fromString(id(initial)))).isFalse();
        call("GET", "/api/questions/" + id(initial), null, null, 404);
    }

    @Test void questionSearchEscapesWildcardsAndPaginates() throws Exception {
        approve(question("Question about 100% plant food"));
        approve(question("Question about 100 plant food"));
        JsonNode filtered = call("GET", "/api/questions?keyword=100%25", null, null, 200).path("data");
        assertThat(filtered.path("totalElements").asInt()).isEqualTo(1);
        JsonNode paged = call("GET", "/api/questions?page=1&size=1", null, null, 200).path("data");
        assertThat(paged.path("items").size()).isEqualTo(1);
        assertThat(paged.path("totalElements").asInt()).isEqualTo(2);
        assertThat(paged.path("last").asBoolean()).isTrue();
    }

    @Test void onlyPublishedVideosAndCommentsArePublic() throws Exception {
        JsonNode published = video("Public vegetable video", "PUBLISHED");
        JsonNode draft = video("Private cooking draft", "DRAFT");
        assertThat(call("GET", "/api/videos", null, null, 200).path("data").path("totalElements").asInt()).isEqualTo(1);
        call("GET", "/api/videos/" + id(published), null, null, 200);
        call("GET", "/api/videos/" + id(draft), null, null, 404);
        call("GET", "/api/videos/" + id(draft) + "/comments", null, null, 404);
        call("POST", "/api/videos/" + id(draft) + "/comments", memberToken, Map.of("content", "hidden"), 404);
        call("PUT", "/api/videos/" + id(draft) + "/favorite", memberToken, null, 404);
        assertThat(call("GET", "/api/admin/videos?status=DRAFT", adminToken, null, 200).path("data").path("totalElements").asInt()).isEqualTo(1);
    }

    @Test void videoSearchCategoryAndPaginationUseDatabase() throws Exception {
        video("100% plant cooking", "PUBLISHED");
        video("100 plant cooking", "PUBLISHED");
        video("Private 100% video", "DRAFT");
        JsonNode data = call("GET", "/api/videos?keyword=100%25&category=mon_chay", null, null, 200).path("data");
        assertThat(data.path("totalElements").asInt()).isEqualTo(1);
        assertThat(call("GET", "/api/videos?keyword=rau&page=1&size=1", null, null, 200).path("data").path("items").size()).isEqualTo(1);
        assertThat(call("GET", "/api/videos?category=DRINKS", null, null, 200).path("data").path("totalElements").asInt()).isZero();
    }

    @Test void onlyAdminCanCreateUpdateOrDeleteVideos() throws Exception {
        JsonNode video = video("Admin managed video", "PUBLISHED");
        call("POST", "/api/admin/videos", memberToken, videoBody("Forged", "PUBLISHED"), 403);
        call("PUT", "/api/admin/videos/" + id(video), memberToken, videoBody("Forged", "PUBLISHED"), 403);
        call("DELETE", "/api/admin/videos/" + id(video) + "?version=0", memberToken, null, 403);
        call("GET", "/api/admin/videos/" + id(video), memberToken, null, 403);
    }

    @Test void videoUpdateRequiresCurrentRevisionAndValidUrls() throws Exception {
        JsonNode video = video("Old title", "PUBLISHED");
        Map<String, Object> update = videoBody("New title", "PUBLISHED");
        call("PUT", "/api/admin/videos/" + id(video), adminToken, update, 400);
        update.put("version", version(video));
        JsonNode updated = call("PUT", "/api/admin/videos/" + id(video), adminToken, update, 200).path("data");
        assertThat(updated.path("title").asText()).isEqualTo("New title");
        assertThat(version(updated)).isEqualTo(1);
        call("PUT", "/api/admin/videos/" + id(video), adminToken, update, 409);
        for (String url : List.of("javascript:alert(1)", "data:text/html,test", "/relative/video", "https://user:password@example.test/video")) {
            var invalid = videoBody("Invalid url", "PUBLISHED"); invalid.put("videoUrl", url);
            call("POST", "/api/admin/videos", adminToken, invalid, 400);
        }
    }

    @Test void membersCanCommentButGuestsCannotAndListsArePaginated() throws Exception {
        JsonNode video = video("Video with conversation", "PUBLISHED");
        String path = "/api/videos/" + id(video) + "/comments";
        call("POST", path, null, Map.of("content", "guest"), 401);
        call("POST", path, memberToken, Map.of("content", "First comment", "authorId", other.getUserId()), 201);
        call("POST", path, otherToken, Map.of("content", "Second comment"), 201);
        JsonNode page = call("GET", path + "?size=1", null, null, 200).path("data");
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("authorId").asText()).isEqualTo(member.getUserId().toString());
    }

    @Test void commentOwnershipRevisionAndAdminRemovalAreEnforced() throws Exception {
        JsonNode video = video("Comment ownership", "PUBLISHED");
        String path = "/api/videos/" + id(video) + "/comments";
        JsonNode comment = call("POST", path, memberToken, Map.of("content", "Original"), 201).path("data");
        String item = path + "/" + id(comment);
        call("PUT", item, otherToken, Map.of("content", "Hijacked", "version", 0), 404);
        call("DELETE", item + "?version=0", otherToken, null, 404);
        JsonNode edited = call("PUT", item, memberToken, Map.of("content", "Edited", "version", 0), 200).path("data");
        assertThat(version(edited)).isEqualTo(1);
        call("PUT", item, memberToken, Map.of("content", "Stale", "version", 0), 409);
        call("DELETE", item + "?version=0", adminToken, null, 409);
        call("DELETE", item + "?version=1", adminToken, null, 200);
        assertThat(call("GET", path, null, null, 200).path("data").path("totalElements").asInt()).isZero();
    }

    @Test void commentIdCannotBeUsedWithAnotherVideo() throws Exception {
        JsonNode first = video("First video", "PUBLISHED");
        JsonNode second = video("Second video", "PUBLISHED");
        JsonNode comment = call("POST", "/api/videos/" + id(first) + "/comments", memberToken, Map.of("content", "My comment"), 201).path("data");
        call("PUT", "/api/videos/" + id(second) + "/comments/" + id(comment), memberToken, Map.of("content", "Moved", "version", 0), 404);
    }

    @Test void favoritesArePrivateIdempotentAndRemovable() throws Exception {
        JsonNode video = video("Favorite cooking video", "PUBLISHED");
        String path = "/api/videos/" + id(video) + "/favorite";
        call("PUT", path, null, null, 401);
        call("PUT", path, memberToken, null, 200);
        call("PUT", path, memberToken, null, 200);
        assertThat(favorites.count()).isEqualTo(1);
        assertThat(call("GET", path, memberToken, null, 200).path("data").path("favorite").asBoolean()).isTrue();
        assertThat(call("GET", path, otherToken, null, 200).path("data").path("favorite").asBoolean()).isFalse();
        assertThat(call("GET", "/api/videos/favorites", otherToken, null, 200).path("data").path("totalElements").asInt()).isZero();
        assertThat(call("GET", "/api/videos/favorites", memberToken, null, 200).path("data").path("totalElements").asInt()).isEqualTo(1);
        call("DELETE", path, memberToken, null, 200);
        call("DELETE", path, memberToken, null, 200);
        assertThat(favorites.count()).isZero();
    }

    @Test void unpublishingHidesSavedVideoAndCommentsButStillAllowsUnsave() throws Exception {
        JsonNode video = video("Soon private", "PUBLISHED");
        String path = "/api/videos/" + id(video);
        call("PUT", path + "/favorite", memberToken, null, 200);
        call("POST", path + "/comments", memberToken, Map.of("content", "Will be hidden"), 201);
        var update = videoBody("Soon private", "DRAFT"); update.put("version", version(video));
        call("PUT", "/api/admin/videos/" + id(video), adminToken, update, 200);
        call("GET", path, null, null, 404);
        call("GET", path + "/comments", null, null, 404);
        assertThat(call("GET", "/api/videos/favorites", memberToken, null, 200).path("data").path("totalElements").asInt()).isZero();
        call("DELETE", path + "/favorite", memberToken, null, 200);
        assertThat(favorites.count()).isZero();
    }

    @Test void deletingVideoRemovesRelatedCommentsAndFavoritesOnly() throws Exception {
        JsonNode target = video("Delete me", "PUBLISHED");
        JsonNode keep = video("Keep me", "PUBLISHED");
        for (JsonNode video : List.of(target, keep)) {
            call("PUT", "/api/videos/" + id(video) + "/favorite", memberToken, null, 200);
            call("POST", "/api/videos/" + id(video) + "/comments", memberToken, Map.of("content", "Related comment"), 201);
        }
        call("DELETE", "/api/admin/videos/" + id(target) + "?version=0", adminToken, null, 200);
        assertThat(videos.existsById(UUID.fromString(id(target)))).isFalse();
        assertThat(videos.existsById(UUID.fromString(id(keep)))).isTrue();
        assertThat(comments.count()).isEqualTo(1);
        assertThat(favorites.count()).isEqualTo(1);
        call("GET", "/api/videos/" + id(target), null, null, 404);
    }

    @Test void demotedAdminCannotUseOldTokenForCommunityAdministration() throws Exception {
        JsonNode q = question("Admin token should not outlive demotion");
        admin.setRole(UserRole.MEMBER); users.saveAndFlush(admin);
        call("PUT", "/api/admin/questions/" + id(q) + "/moderation", adminToken, Map.of("decision", "APPROVE", "version", 0), 403);
        call("POST", "/api/admin/videos", adminToken, videoBody("Denied", "PUBLISHED"), 403);
        call("GET", "/api/admin/questions", adminToken, null, 403);
    }

    @Test void lockedMemberCannotSubmitCommentOrFavoriteWithOldToken() throws Exception {
        JsonNode video = video("Account status guarded", "PUBLISHED");
        member.setStatus(AccountStatus.LOCKED); users.saveAndFlush(member);
        call("POST", "/api/questions", memberToken, Map.of("content", "Blocked submission"), 403);
        call("GET", "/api/questions/mine", memberToken, null, 403);
        call("POST", "/api/videos/" + id(video) + "/comments", memberToken, Map.of("content", "Blocked"), 403);
        call("PUT", "/api/videos/" + id(video) + "/favorite", memberToken, null, 403);
    }

    @Test void invalidPayloadsPagesAndIdentifiersReturn400Not500() throws Exception {
        call("POST", "/api/questions", memberToken, Map.of("content", "     "), 400);
        call("POST", "/api/questions", memberToken, Map.of("content", "x".repeat(5001)), 400);
        for (String path : List.of("/api/questions?page=-1", "/api/questions?size=0", "/api/videos?size=51", "/api/videos/not-a-uuid", "/api/admin/questions?status=BAD")) {
            call("GET", path, adminToken, null, 400);
        }
        var body = videoBody("Bad duration", "PUBLISHED"); body.put("durationSeconds", 0);
        call("POST", "/api/admin/videos", adminToken, body, 400);
    }

    @Test void missingDeleteRevisionIsBadRequestRatherThanServerError() throws Exception {
        JsonNode video = video("Missing delete revision", "PUBLISHED");
        JsonNode question = question("Missing question delete revision");
        call("DELETE", "/api/admin/videos/" + id(video), adminToken, null, 400);
        call("DELETE", "/api/admin/questions/" + id(question), adminToken, null, 400);
        assertThat(videos.existsById(UUID.fromString(id(video)))).isTrue();
        assertThat(questions.existsById(UUID.fromString(id(question)))).isTrue();
    }

    @Test void publicPublishedQuestionCanBeHiddenAgainByModeration() throws Exception {
        JsonNode q = approve(question("Initially approved question"));
        call("PUT", "/api/admin/questions/" + id(q) + "/moderation", adminToken,
                Map.of("decision", "REJECT", "reason", "Needs correction", "version", version(q)), 200);
        call("GET", "/api/questions/" + id(q), null, null, 404);
        assertThat(call("GET", "/api/questions", null, null, 200).path("data").path("totalElements").asInt()).isZero();
    }

    @Test void favoritesPaginationNeverIncludesAnotherMembersSavedVideo() throws Exception {
        JsonNode first = video("First saved video", "PUBLISHED");
        JsonNode second = video("Second saved video", "PUBLISHED");
        call("PUT", "/api/videos/" + id(first) + "/favorite", memberToken, null, 200);
        call("PUT", "/api/videos/" + id(second) + "/favorite", otherToken, null, 200);
        JsonNode saved = call("GET", "/api/videos/favorites?page=0&size=1", memberToken, null, 200).path("data");
        assertThat(saved.path("totalElements").asInt()).isEqualTo(1);
        assertThat(saved.path("items").get(0).path("id").asText()).isEqualTo(id(first));
    }
}
