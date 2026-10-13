package com.vegeai.backend.modules.recipe.service;

import com.vegeai.backend.common.exception.AppException;
import com.vegeai.backend.common.exception.ErrorCode;
import com.vegeai.backend.modules.recipe.dto.CreateTagRequest;
import com.vegeai.backend.modules.recipe.dto.TagDto;
import com.vegeai.backend.modules.recipe.dto.TagGroupResponse;
import com.vegeai.backend.modules.recipe.entity.Tag;
import com.vegeai.backend.modules.recipe.entity.TagGroup;
import com.vegeai.backend.modules.recipe.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagService {

    private final TagRepository tagRepository;

    /**
     * Lấy toàn bộ cây danh mục thẻ gom nhóm theo 6 nhóm phân loại chuẩn hóa (Bộ lọc đa tầng).
     */
    @Transactional(readOnly = true)
    public TagGroupResponse getAllTagsGrouped() {
        List<Tag> allTags = tagRepository.findAllByOrderByGroupAscNameAsc();

        List<TagDto> dietaryType = allTags.stream().filter(t -> t.getGroup() == TagGroup.DIETARY_TYPE).map(this::mapToDto).toList();
        List<TagDto> cookingMethod = allTags.stream().filter(t -> t.getGroup() == TagGroup.COOKING_METHOD).map(this::mapToDto).toList();
        List<TagDto> dishCategory = allTags.stream().filter(t -> t.getGroup() == TagGroup.DISH_CATEGORY).map(this::mapToDto).toList();
        List<TagDto> nutritionGoal = allTags.stream().filter(t -> t.getGroup() == TagGroup.NUTRITION_GOAL).map(this::mapToDto).toList();
        List<TagDto> occasion = allTags.stream().filter(t -> t.getGroup() == TagGroup.OCCASION).map(this::mapToDto).toList();
        List<TagDto> dietaryRestriction = allTags.stream().filter(t -> t.getGroup() == TagGroup.DIETARY_RESTRICTION).map(this::mapToDto).toList();

        return TagGroupResponse.builder()
                .dietaryType(dietaryType)
                .cookingMethod(cookingMethod)
                .dishCategory(dishCategory)
                .nutritionGoal(nutritionGoal)
                .occasion(occasion)
                .dietaryRestriction(dietaryRestriction)
                .build();
    }

    /**
     * Lấy danh sách tất cả các thẻ tag phẳng.
     */
    @Transactional(readOnly = true)
    public List<TagDto> getAllTags() {
        return tagRepository.findAllByOrderByGroupAscNameAsc().stream()
                .map(this::mapToDto)
                .toList();
    }

    /**
     * Thêm mới một thẻ phân loại vào từ điển hệ thống (Admin).
     */
    @Transactional
    public TagDto createTag(CreateTagRequest request) {
        String cleanCode = request.getCode().trim().toUpperCase();
        if (tagRepository.existsByCodeIgnoreCase(cleanCode)) {
            throw new AppException(ErrorCode.TAG_ALREADY_EXISTS);
        }

        Tag tag = Tag.builder()
                .code(cleanCode)
                .name(request.getName().trim())
                .group(request.getGroup())
                .description(request.getDescription())
                .build();

        Tag saved = tagRepository.save(tag);
        log.info("[TAG] Created new tag code='{}', group='{}'", saved.getCode(), saved.getGroup());
        return mapToDto(saved);
    }

    /**
     * Xóa một thẻ tag (Admin).
     */
    @Transactional
    public void deleteTag(UUID tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new AppException(ErrorCode.TAG_NOT_FOUND));
        tagRepository.delete(tag);
        log.info("[TAG] Deleted tag id='{}', code='{}'", tagId, tag.getCode());
    }

    public TagDto mapToDto(Tag tag) {
        return TagDto.builder()
                .tagId(tag.getTagId())
                .code(tag.getCode())
                .name(tag.getName())
                .group(tag.getGroup())
                .description(tag.getDescription())
                .build();
    }
}
