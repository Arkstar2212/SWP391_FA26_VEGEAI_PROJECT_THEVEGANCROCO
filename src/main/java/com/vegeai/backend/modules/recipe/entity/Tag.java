package com.vegeai.backend.modules.recipe.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tags", indexes = {
        @Index(name = "idx_tags_code", columnList = "code", unique = true),
        @Index(name = "idx_tags_group", columnList = "tag_group")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tag_id", updatable = false, nullable = false)
    private UUID tagId;

    @Column(name = "code", length = 50, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_group", length = 30, nullable = false)
    private TagGroup group;

    @Column(name = "description", length = 255)
    private String description;
}
