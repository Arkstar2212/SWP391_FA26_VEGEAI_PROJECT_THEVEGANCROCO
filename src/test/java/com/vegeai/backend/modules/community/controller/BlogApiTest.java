package com.vegeai.backend.modules.community.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.auth.service.*;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.community.repository.VideoRepository;
import com.vegeai.backend.modules.community.entity.CommunityVideo;
import com.vegeai.backend.modules.community.entity.VideoStatus;
import com.vegeai.backend.modules.interactions.entity.ContentComment;
import com.vegeai.backend.modules.interactions.entity.ContentRating;
import com.vegeai.backend.modules.interactions.entity.InteractionTarget;
import com.vegeai.backend.modules.interactions.repository.ContentCommentRepository;
import com.vegeai.backend.modules.interactions.repository.ContentRatingRepository;
import com.vegeai.backend.modules.interactions.service.ContentInteractionAssetService;
import com.vegeai.backend.modules.user.entity.*;
import com.vegeai.backend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class BlogApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired BlogPostRepository posts;
    @Autowired VideoRepository videos;
    @Autowired ContentCommentRepository comments;
    @Autowired ContentRatingRepository ratings;
    @Autowired ContentInteractionAssetService interactionAssets;
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
        return mapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
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
    JsonNode publish(JsonNode post) throws Exception { return review(submit(post), "APPROVE", "", 200); }

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

    @Test void membersCanRatePublishedVideosAndGuestsCanReadTheirScore() throws Exception {
        CommunityVideo video = new CommunityVideo();
        video.setTitle("Video fixture");
        video.setVideoUrl("https://example.test/video.mp4");
        video.setStatus(VideoStatus.PUBLISHED);
        video.setCreatedBy(member);
        video = videos.saveAndFlush(video);
        String target = "/api/content-interactions/VIDEO/" + video.getId();

        call("GET", "/api/public/content-interactions/VIDEO/" + video.getId() + "?page=0&size=10", null, null, 200);
        call("PUT", target + "/rating", null, Map.of("stars", 4), 401);
        JsonNode saved = call("PUT", target + "/rating", token, Map.of("stars", 4), 200).path("data");
        assertThat(saved.path("targetType").asText()).isEqualTo("VIDEO");
        assertThat(saved.path("averageStars").asDouble()).isEqualTo(4.0);
        assertThat(saved.path("ratingCount").asInt()).isEqualTo(1);

        JsonNode summary = call("GET", "/api/public/content-interactions/VIDEO/ratings?ids=" + video.getId(),
                null, null, 200).path("data").get(0);
        assertThat(summary.path("averageStars").asDouble()).isEqualTo(4.0);
        assertThat(summary.path("ratingCount").asInt()).isEqualTo(1);
        call("DELETE", target + "/rating", token, null, 200);
    }

    @Test void membersCanSubmitStructuredReviewsAndHideTheirPublicName() throws Exception {
        JsonNode post = publish(draft("Structured review story"));
        String postId = id(post);
        MockMultipartFile review = new MockMultipartFile("review", "", "application/json",
                mapper.writeValueAsBytes(Map.of("content", "A lovely, easy recipe to make.",
                        "stars", 5, "tags", List.of("Dễ làm", "Cả nhà đều thích"),
                        "publicDisplayName", false, "photoUrls", List.of())));
        ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", imageBytes);
        MockMultipartFile photo = new MockMultipartFile("photos", "dish.png", "image/png", imageBytes.toByteArray());
        JsonNode saved = mapper.readTree(mvc.perform(multipart("/api/content-interactions/BLOG/{id}/reviews", postId)
                        .file(review).file(photo).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");

        assertThat(saved.path("reviewStars").asInt()).isEqualTo(5);
        assertThat(saved.path("tags").size()).isEqualTo(2);
        assertThat(saved.path("photoUrls").size()).isEqualTo(1);
        assertThat(saved.path("publicDisplayName").asBoolean()).isFalse();
        String photoUrl = saved.path("photoUrls").get(0).asText();
        mvc.perform(request(HttpMethod.GET, URI.create(photoUrl)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        interactionAssets.delete(List.of(photoUrl));
        JsonNode listed = call("GET", "/api/public/content-interactions/BLOG/" + postId, null, null, 200)
                .path("data").path("comments").path("items").get(0);
        assertThat(listed.path("authorName").asText()).isEqualTo("Ẩn danh");
        assertThat(listed.path("content").asText()).isEqualTo("A lovely, easy recipe to make.");
        assertThat(call("GET", "/api/public/content-interactions/BLOG/" + postId, null, null, 200)
                .path("data").path("rating").path("ratingCount").asLong()).isEqualTo(1);
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
        JsonNode reviewDetail = call("GET", "/api/admin/blog/posts/" + id(post), adminToken, null, 200).path("data");
        assertThat(reviewDetail.path("title").asText()).isEqualTo("A green kitchen story");
        assertThat(reviewDetail.path("excerpt").asText()).isEqualTo("A plant-based kitchen story");
        assertThat(reviewDetail.path("content").asText()).contains("vegetables and plant-based cooking");
        assertThat(reviewDetail.path("coverImage").asText()).isEqualTo("/images/fresh-vegetables.png");
        assertThat(reviewDetail.path("category").asText()).isEqualTo("GREEN_LIVING");
        assertThat(reviewDetail.path("authorName").asText()).isEqualTo(member.getFullName());
        assertThat(reviewDetail.path("submittedAt").asText()).isNotBlank();
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
        call("DELETE", own(post) + "?version=" + version(post), token, null, 200);
        assertThat(call("GET", own(post), token, null, 200).path("data").path("status").asText())
                .isEqualTo("ARCHIVED");
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

    @Test void pendingPostCanBeArchivedAndRestoredBeforeWithdrawal() throws Exception {
        JsonNode post = submit(draft("Pending story"));
        call("PUT", own(post), token, withVersion(input("Changed while pending"), version(post)), 409);
        call("DELETE", own(post) + "?version=" + version(post), token, null, 200);
        post = call("GET", own(post), token, null, 200).path("data");
        assertThat(post.path("status").asText()).isEqualTo("ARCHIVED");
        post = call("POST", own(post) + "/restore", token, Map.of("version", version(post)), 200).path("data");
        assertThat(post.path("status").asText()).isEqualTo("PENDING_REVIEW");
        post = call("POST", own(post) + "/withdraw", token, Map.of("version", version(post)), 200).path("data");
        assertThat(post.path("status").asText()).isEqualTo("DRAFT");
        call("DELETE", own(post) + "?version=" + version(post), token, null, 200);
        JsonNode archived = call("GET", own(post), token, null, 200).path("data");
        assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");
        call("DELETE", own(post) + "/permanent?version=" + version(archived), token, null, 200);
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
        call("PUT", "/api/admin/blog/posts/" + id(post), token, withVersion(input("Unauthorized edit"), version(post)), 403);
        call("POST", "/api/admin/blog/posts/" + id(post) + "/archive", token, Map.of("version", version(post)), 403);
        call("DELETE", "/api/admin/blog/posts/" + id(post) + "?version=" + version(post), token, null, 403);
        review(post, "APPROVE", "", 409);
    }

    @Test void adminCanEditArchiveRestoreAndPermanentlyDeleteManagedPosts() throws Exception {
        JsonNode post = publish(draft("Admin-managed story"));
        String path = "/api/admin/blog/posts/" + id(post);
        Map<String, Object> edit = withVersion(input("Admin-edited story"), version(post));

        call("PUT", path, token, edit, 403);
        JsonNode edited = call("PUT", path, adminToken, edit, 200).path("data");
        assertThat(edited.path("title").asText()).isEqualTo("Admin-edited story");
        assertThat(edited.path("status").asText()).isEqualTo("PUBLISHED");
        call("PUT", path, adminToken, withVersion(input("Stale admin edit"), version(post)), 409);

        JsonNode archived = call("POST", path + "/archive", adminToken,
                Map.of("version", version(edited)), 200).path("data");
        assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");
        call("GET", "/api/blog/posts/" + id(post), null, null, 404);
        JsonNode restored = call("POST", path + "/restore", adminToken,
                Map.of("version", version(archived)), 200).path("data");
        assertThat(restored.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(call("GET", "/api/blog/posts/" + id(post), null, null, 200)
                .path("data").path("title").asText()).isEqualTo("Admin-edited story");
    }

    @Test void adminPermanentDeleteRemovesBlogCommentsAndRatings() throws Exception {
        JsonNode post = publish(draft("Admin delete story"));
        UUID postId = UUID.fromString(id(post));
        ContentComment comment = new ContentComment();
        comment.setTargetType(InteractionTarget.BLOG);
        comment.setTargetId(postId);
        comment.setAuthor(member);
        comment.setContent("A comment on this post.");
        comments.saveAndFlush(comment);
        ContentRating rating = new ContentRating();
        rating.setTargetType(InteractionTarget.BLOG);
        rating.setTargetId(postId);
        rating.setUser(member);
        rating.setStars(4);
        ratings.saveAndFlush(rating);

        call("DELETE", "/api/admin/blog/posts/" + postId + "?version=" + version(post),
                token, null, 403);
        call("DELETE", "/api/admin/blog/posts/" + postId + "?version=" + (version(post) - 1),
                adminToken, null, 409);
        call("DELETE", "/api/admin/blog/posts/" + postId + "?version=" + version(post),
                adminToken, null, 200);

        assertThat(posts.findById(postId)).isEmpty();
        assertThat(comments.findByTargetTypeAndTargetId(InteractionTarget.BLOG, postId, PageRequest.of(0, 10))
                .getTotalElements()).isZero();
        assertThat(ratings.countByTargetTypeAndTargetId(InteractionTarget.BLOG, postId)).isZero();
    }

    @Test void publishedBlogReadersCanCommentAndVote() throws Exception {
        JsonNode post = publish(draft("Community interaction story"));
        String contentPath = "/api/public/content-interactions/BLOG/" + id(post);

        assertThat(call("GET", contentPath, null, null, 200).path("data").path("rating").path("ratingCount").asLong())
                .isZero();
        call("PUT", "/api/content-interactions/BLOG/" + id(post) + "/rating", null, Map.of("stars", 5), 401);
        JsonNode vote = call("PUT", "/api/content-interactions/BLOG/" + id(post) + "/rating",
                token, Map.of("stars", 5), 200).path("data");
        assertThat(vote.path("myRating").asInt()).isEqualTo(5);
        assertThat(vote.path("ratingCount").asLong()).isEqualTo(1);

        JsonNode comment = call("POST", "/api/content-interactions/BLOG/" + id(post) + "/comments",
                token, Map.of("content", "A useful blog comment."), 201).path("data");
        String commentPath = "/api/content-interactions/BLOG/" + id(post) + "/comments/" + comment.path("id").asText();
        JsonNode edited = call("PUT", commentPath, token,
                Map.of("content", "An updated blog comment.", "version", comment.path("version").asLong()), 200)
                .path("data");
        assertThat(edited.path("content").asText()).isEqualTo("An updated blog comment.");
        assertThat(call("GET", contentPath, null, null, 200).path("data").path("comments").path("totalElements").asLong())
                .isEqualTo(1);
        call("DELETE", commentPath + "?version=" + edited.path("version").asLong(), otherToken, null, 404);
        call("DELETE", commentPath + "?version=" + edited.path("version").asLong(), token, null, 200);
        call("DELETE", "/api/content-interactions/BLOG/" + id(post) + "/rating", token, null, 200);
        assertThat(call("GET", contentPath, null, null, 200).path("data").path("rating").path("ratingCount").asLong())
                .isZero();
    }

    @Test void adminDraftPublishesImmediatelyAndCanEditOwnPublishedPost() throws Exception {
        JsonNode post = call("POST", "/api/blog/my-posts", adminToken, input("Admin kitchen story"), 201).path("data");
        assertThat(post.path("status").asText()).isEqualTo("DRAFT");
        call("GET", "/api/blog/posts/" + id(post), null, null, 404);
        post = call("POST", own(post) + "/submit", adminToken, Map.of("version", version(post)), 200).path("data");
        assertThat(post.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(post.path("publishedAt").asText()).isNotBlank();
        assertThat(call("GET", "/api/admin/blog/posts?status=PENDING_REVIEW", adminToken, null, 200)
                .path("data").path("totalElements").asInt()).isZero();
        JsonNode edited = call("PUT", own(post), adminToken, withVersion(input("Edited admin story"), version(post)), 200).path("data");
        assertThat(edited.path("status").asText()).isEqualTo("PUBLISHED");
        call("PUT", own(post), adminToken, withVersion(input("Stale admin story"), version(post)), 409);
        call("PUT", own(edited), otherToken, withVersion(input("Not my story"), version(edited)), 404);
        assertThat(call("GET", "/api/blog/posts/" + id(post), null, null, 200).path("data").path("title").asText()).isEqualTo("Edited admin story");
    }

    @Test void directPublicationUsesCurrentDatabaseRoleAndValidatesContent() throws Exception {
        var shortInput = input("Hi"); shortInput.put("content", "Short draft");
        JsonNode shortPost = call("POST", "/api/blog/my-posts", adminToken, shortInput, 201).path("data");
        call("POST", own(shortPost) + "/submit", adminToken, Map.of("version", version(shortPost)), 400);
        JsonNode post = call("POST", "/api/blog/my-posts", adminToken, input("Former admin story"), 201).path("data");
        admin.setRole(UserRole.MEMBER); users.saveAndFlush(admin);
        post = call("POST", own(post) + "/submit", adminToken, Map.of("version", version(post)), 200).path("data");
        assertThat(post.path("status").asText()).isEqualTo("PENDING_REVIEW");
    }

    @Test void editingPublishedPostRequiresReviewBeforeUpdatedVersionIsPublic() throws Exception {
        JsonNode published = publish(draft("Published kitchen story"));
        Map<String, Object> changed = withVersion(input("Updated kitchen story"), version(published));

        call("PUT", own(published) + "/request-review", otherToken, changed, 404);
        call("PUT", own(published) + "/request-review", token,
                withVersion(input("Stale edit"), version(published) - 1), 409);
        JsonNode pending = call("PUT", own(published) + "/request-review", token, changed, 200).path("data");

        assertThat(pending.path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(version(pending)).isGreaterThan(version(published));
        assertThat(pending.path("title").asText()).isEqualTo("Updated kitchen story");
        call("GET", "/api/blog/posts/" + id(pending), null, null, 404);
        assertThat(call("GET", "/api/admin/blog/posts?status=PENDING_REVIEW", adminToken, null, 200)
                .path("data").path("totalElements").asInt()).isEqualTo(1);

        JsonNode approved = review(pending, "APPROVE", "", 200);
        assertThat(call("GET", "/api/blog/posts/" + id(approved), null, null, 200)
                .path("data").path("title").asText()).isEqualTo("Updated kitchen story");
    }

    @Test void archiveHidesPublishedPostRestoreReturnsItAndOwnerCanDeleteDirectly() throws Exception {
        JsonNode published = publish(draft("Story to hide"));
        String path = own(published);

        call("DELETE", path + "?version=" + version(published), token, null, 200);
        JsonNode archived = call("GET", path, token, null, 200).path("data");
        assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");
        call("GET", "/api/blog/posts/" + id(published), null, null, 404);
        call("POST", path + "/restore", otherToken, Map.of("version", version(archived)), 404);
        call("POST", path + "/restore", token, Map.of("version", version(published)), 409);

        JsonNode restored = call("POST", path + "/restore", token,
                Map.of("version", version(archived)), 200).path("data");
        assertThat(restored.path("status").asText()).isEqualTo("PUBLISHED");
        call("GET", "/api/blog/posts/" + id(restored), null, null, 200);

        call("DELETE", path + "/permanent?version=" + version(restored), token, null, 200);
        call("GET", path, token, null, 404);
    }

    @Test void ownerCanDeletePublishedPostAndItsCommentsAndRatingsButOthersCannot() throws Exception {
        JsonNode published = publish(draft("Story to delete"));
        UUID postId = UUID.fromString(id(published));

        ContentComment comment = new ContentComment();
        comment.setTargetType(InteractionTarget.BLOG);
        comment.setTargetId(postId);
        comment.setAuthor(other);
        comment.setContent("A comment on this post.");
        comments.saveAndFlush(comment);

        ContentRating rating = new ContentRating();
        rating.setTargetType(InteractionTarget.BLOG);
        rating.setTargetId(postId);
        rating.setUser(other);
        rating.setStars(5);
        ratings.saveAndFlush(rating);

        call("DELETE", "/api/blog/my-posts/" + postId + "/permanent?version=" + version(published),
                otherToken, null, 404);
        call("DELETE", "/api/blog/my-posts/" + postId + "/permanent?version=" + (version(published) - 1),
                token, null, 409);
        assertThat(posts.findById(postId)).isPresent();

        JsonNode archived = call("POST", "/api/blog/my-posts/" + postId + "/archive", token,
                Map.of("version", version(published)), 200).path("data");
        assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");
        call("GET", "/api/blog/posts/" + postId, null, null, 404);
        archived = call("POST", "/api/blog/my-posts/" + postId + "/restore", token,
                Map.of("version", version(archived)), 200).path("data");
        assertThat(archived.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(call("GET", "/api/blog/posts/" + postId, null, null, 200).path("data").path("postId").asText())
                .isEqualTo(postId.toString());

        archived = call("POST", "/api/blog/my-posts/" + postId + "/archive", token,
                Map.of("version", version(archived)), 200).path("data");
        call("DELETE", "/api/blog/my-posts/" + postId + "/permanent?version=" + version(published),
                token, null, 409);
        call("DELETE", "/api/blog/my-posts/" + postId + "/permanent?version=" + version(archived),
                token, null, 200);

        assertThat(posts.findById(postId)).isEmpty();
        assertThat(comments.findByTargetTypeAndTargetId(InteractionTarget.BLOG, postId, PageRequest.of(0, 10))
                .getTotalElements()).isZero();
        assertThat(ratings.countByTargetTypeAndTargetId(InteractionTarget.BLOG, postId)).isZero();
    }

    @Test void lockedAccountAndDemotedAdminAreRecheckedDespiteExistingTokens() throws Exception {
        member.setStatus(AccountStatus.LOCKED); users.saveAndFlush(member);
        call("GET", "/api/blog/my-posts", token, null, 403);
        admin.setRole(UserRole.MEMBER); users.saveAndFlush(admin);
        call("GET", "/api/admin/blog/posts", adminToken, null, 403);
    }

    @Test void publicRatingSummaryBatchDoesNotRequireCommentPage() throws Exception {
        JsonNode published = publish(draft("Rated batch story"));
        JsonNode unrated = publish(draft("Unrated batch story"));
        ContentRating rating = new ContentRating();
        rating.setTargetType(InteractionTarget.BLOG);
        rating.setTargetId(UUID.fromString(id(published)));
        rating.setUser(member);
        rating.setStars(5);
        ratings.saveAndFlush(rating);

        JsonNode response = call("GET", "/api/public/content-interactions/BLOG/ratings?ids="
                + id(published) + "&ids=" + id(unrated), null, null, 200).path("data");

        assertThat(response).hasSize(2);
        assertThat(response.get(0).path("averageStars").asDouble()).isEqualTo(5.0);
        assertThat(response.get(0).path("ratingCount").asLong()).isEqualTo(1);
        assertThat(response.get(1).path("averageStars").isNull()).isTrue();
        assertThat(response.get(1).path("ratingCount").asLong()).isZero();
    }
}
