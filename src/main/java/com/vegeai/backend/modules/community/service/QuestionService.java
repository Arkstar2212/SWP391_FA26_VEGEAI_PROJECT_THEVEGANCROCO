package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.*;
import com.vegeai.backend.modules.community.dto.CommunityDtos.*;
import com.vegeai.backend.modules.community.entity.*;
import com.vegeai.backend.modules.community.repository.QuestionRepository;
import com.vegeai.backend.modules.notification.service.NotificationService;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional
public class QuestionService {
    private final QuestionRepository questions;
    private final CommunityAccess access;
    private final NotificationService notificationService;

    @PreAuthorize("hasRole('MEMBER')")
    public QuestionResponse create(String principal, QuestionInput input) {
        User user = questioner(principal);
        String content = input.content().strip();
        if (content.length() < 5) throw new AppException(ErrorCode.VALIDATION_ERROR, "Question must contain at least 5 characters.");
        CommunityQuestion question = new CommunityQuestion();
        question.setAuthor(user);
        question.setContent(content);
        CommunityQuestion saved = questions.saveAndFlush(question);
        // Notify moderators only after the submitted question has been persisted successfully.
        notificationService.notifyAdministrators("QUESTION_SUBMITTED", "Câu hỏi mới cần được duyệt",
                "Một thành viên vừa gửi câu hỏi mới lên cộng đồng.", "/admin/questions/" + saved.getId());
        return response(saved, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> published(String keyword, Boolean answered, int page, int size) {
        return list(QuestionStatus.APPROVED, null, keyword, answered, page, size, false);
    }

    @Transactional(readOnly = true)
    public QuestionResponse published(UUID id) {
        CommunityQuestion question = questions.findById(id).filter(q -> q.getStatus() == QuestionStatus.APPROVED)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        return response(question, false);
    }

    @PreAuthorize("hasRole('MEMBER')") @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> mine(String principal, QuestionStatus status, int page, int size) {
        return list(status, questioner(principal).getUserId(), null, null, page, size, true);
    }

    @PreAuthorize("hasRole('MEMBER')") @Transactional(readOnly = true)
    public QuestionResponse mine(String principal, UUID id) {
        UUID author = questioner(principal).getUserId();
        CommunityQuestion question = questions.findById(id).filter(q -> q.getAuthor().getUserId().equals(author))
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        return response(question, true);
    }

    @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> queue(String principal, QuestionStatus status, String keyword, int page, int size) {
        access.admin(principal);
        return list(status, null, keyword, null, page, size, true);
    }

    @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true)
    public QuestionResponse adminDetail(String principal, UUID id) {
        access.admin(principal);
        return response(questions.findById(id).orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND)), true);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public QuestionResponse review(String principal, UUID id, ReviewInput input) {
        User admin = access.admin(principal);
        if (input.decision() == Decision.REJECT && (input.reason() == null || input.reason().isBlank())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "A rejection reason is required.");
        }
        CommunityQuestion question = locked(id, input.version());
        question.setStatus(input.decision() == Decision.APPROVE ? QuestionStatus.APPROVED : QuestionStatus.REJECTED);
        question.setModerationReason(input.decision() == Decision.REJECT ? input.reason().strip() : null);
        question.setReviewedBy(admin);
        question.setReviewedAt(LocalDateTime.now());
        questions.flush();
        // Let the question owner know the moderation decision; the notice remains private to that owner.
        notificationService.notifyUser(question.getAuthor(), "QUESTION_REVIEWED", "Câu hỏi đã được xem xét",
                input.decision() == Decision.APPROVE
                        ? "Câu hỏi của bạn đã được duyệt và xuất hiện trong cộng đồng."
                        : "Câu hỏi của bạn chưa được duyệt. Mở trang câu hỏi của bạn để xem thông tin chi tiết.",
                "/questions/mine/" + question.getId());
        return response(question, true);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public QuestionResponse answer(String principal, UUID id, AnswerInput input) {
        User admin = access.admin(principal);
        CommunityQuestion question = locked(id, input.version());
        question.setAnswer(input.content().strip());
        question.setAnsweredBy(admin);
        question.setAnsweredAt(LocalDateTime.now());
        questions.flush();
        // An answer is a direct community response, so notify the original question author.
        notificationService.notifyUser(question.getAuthor(), "QUESTION_ANSWERED", "Câu hỏi của bạn đã được trả lời",
                "Quản trị viên đã gửi câu trả lời cho câu hỏi của bạn.", "/questions/mine/" + question.getId());
        return response(question, true);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void delete(String principal, UUID id, long version) {
        access.admin(principal);
        questions.delete(locked(id, version));
        questions.flush();
    }

    private User questioner(String principal) {
        User user = access.user(principal);
        if (user.getRole() != UserRole.MEMBER) throw new AppException(ErrorCode.FORBIDDEN);
        return user;
    }

    private CommunityQuestion locked(UUID id, Long version) {
        CommunityQuestion question = questions.findForUpdate(id).orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        access.revision(question.getVersion(), version);
        return question;
    }

    private PageResponse<QuestionResponse> list(QuestionStatus status, UUID author, String keyword, Boolean answered, int page, int size, boolean privateFields) {
        String pattern = access.keyword(keyword);
        Specification<CommunityQuestion> specification = (root, query, cb) -> cb.conjunction();
        if (status != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (author != null) specification = specification.and((root, query, cb) -> cb.equal(root.get("author").get("userId"), author));
        if (pattern != null) specification = specification.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("content")), pattern, '\\'), cb.like(cb.lower(root.get("answer")), pattern, '\\')));
        if (answered != null) specification = specification.and((root, query, cb) -> answered ? cb.isNotNull(root.get("answer")) : cb.isNull(root.get("answer")));
        return PageResponse.from(questions.findAll(specification, access.page(page, size)).map(q -> response(q, privateFields)));
    }

    private QuestionResponse response(CommunityQuestion q, boolean privateFields) {
        return new QuestionResponse(q.getId(), access.name(q.getAuthor()), q.getContent(), q.getStatus(), q.getAnswer(),
                q.getAnsweredBy() == null ? null : access.name(q.getAnsweredBy()), q.getAnsweredAt(),
                privateFields ? q.getModerationReason() : null,
                privateFields && q.getReviewedBy() != null ? access.name(q.getReviewedBy()) : null,
                privateFields ? q.getReviewedAt() : null, q.getCreatedAt(), q.getUpdatedAt(), q.getVersion());
    }
}
