package com.DoAn1.examservice.domain.entity;

import java.util.UUID;

import com.DoAn1.examservice.domain.enums.QuestionType;
import com.DoAn1.examservice.util.UuidV7Generator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "question", indexes = {
        @Index(name = "idx_question_grade_type_topic", columnList = "gradeId,questionType,questionTopic")
})
public class Question extends AuditableEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID questionUuid;

    @Column(nullable = false)
    private Long gradeId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionContent;

    @Column(length = 1000)
    private String imagePath;

    @Column(length = 255)
    private String questionTopic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuestionType questionType;

    @Column(nullable = false)
    private UUID createdByUserUuid;

    @Column(nullable = false)
    private Boolean isActive = true;

    @PrePersist
    void prePersist() {
        if (questionUuid == null) {
            questionUuid = UuidV7Generator.generate();
        }
    }
}

