package com.vegeai.backend.modules.recipe.repository;

import com.vegeai.backend.modules.recipe.entity.Tag;
import com.vegeai.backend.modules.recipe.entity.TagGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Tag> findByGroupOrderByCodeAsc(TagGroup group);

    List<Tag> findByCodeIn(Collection<String> codes);

    List<Tag> findAllByOrderByGroupAscNameAsc();
}
