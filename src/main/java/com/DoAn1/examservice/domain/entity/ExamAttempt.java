package com.DoAn1.examservice.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.AttemptStatus;
import com.DoAn1.examservice.domain.enums.SubmitSource;
import com.DoAn1.examservice.util.UuidV7Generator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exam_attempt", uniqueConstraints = {
        @UniqueConstraint(name = "uk_exam_attempt_exam_student_attempt_no", columnNames = { "examUuid", "studentUuid", "attemptNo" })
}, indexes = {
        @Index(name = "idx_exam_attempt_exam_student", columnList = "examUuid,studentUuid")
})
public class ExamAttempt {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID attemptUuid;

    @Column(nullable = false)
    private UUID examUuid;

    @Column(nullable = false)
    private UUID studentUuid;

    @Column(length = 20)
    private String studentId;

    @Column(length = 255)
    private String studentFullname;

    @Column(nullable = false)
    private Integer attemptNo;

    private Instant startedAt;

    private Instant submittedAt;

    private Integer timeSpentSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status;

    @Column(precision = 10, scale = 2)
    private BigDecimal score;

    @Column(nullable = false)
    private Boolean isAutoSubmitted = false;

    private Integer violationCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmitSource submitSource;

    @Column(length = 1000)
    private String rawImageUrl;

    @Column(length = 1000)
    private String scoredImageUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionSnapshotJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (attemptUuid == null) {
            attemptUuid = UuidV7Generator.generate();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

