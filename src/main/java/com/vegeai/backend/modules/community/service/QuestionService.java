package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.common.dto.PageResponse;
import com.vegeai.backend.common.exception.*;
import com.vegeai.backend.modules.community.dto.CommunityDtos.*;
import com.vegeai.backend.modules.community.entity.*;
import com.vegeai.backend.modules.community.repository.QuestionRepository;
import com.vegeai.backend.modules.user.entity.User;
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

    public QuestionResponse create(String principal, QuestionInput input) {
        User user = access.user(principal);
        String content = input.content().strip();
        if (content.length() < 5) throw new AppException(ErrorCode.VALIDATION_ERROR, "Question must contain at least 5 characters.");
        CommunityQuestion question = new CommunityQuestion();
        question.setAuthor(user);
        question.setContent(content);
        return response(questions.saveAndFlush(question), true);
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

    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> mine(String principal, QuestionStatus status, int page, int size) {
        return list(status, access.user(principal).getUserId(), null, null, page, size, true);
    }

    @Transactional(readOnly = true)
    public QuestionResponse mine(String principal, UUID id) {
        UUID author = access.user(principal).getUserId();
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
        return response(question, true);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void delete(String principal, UUID id, long version) {
        access.admin(principal);
        questions.delete(locked(id, version));
        questions.flush();
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
