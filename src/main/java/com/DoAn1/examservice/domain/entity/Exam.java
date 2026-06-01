package com.DoAn1.examservice.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.ExamStatus;
import com.DoAn1.examservice.domain.enums.ExamType;
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
@Table(name = "exam", indexes = {
        @Index(name = "idx_exam_grade_status_start_end", columnList = "gradeId,status,startTime,endTime")
})
public class Exam extends AuditableEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID examUuid;

    @Column(nullable = false, length = 255)
    private String examName;

    @Column(nullable = false)
    private Long gradeId;

    @Column(length = 20)
    private String schoolYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamType examType;

    private Instant startTime;

    private Instant endTime;

    @Column(nullable = false)
    private Integer durationMinutes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalScore;

    @Column(nullable = false)
    private Integer numberOfAttempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamStatus status;

    @Column(nullable = false)
    private UUID createdByUserUuid;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal tfCorrect1Pct = BigDecimal.TEN;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal tfCorrect2Pct = new BigDecimal("25");

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal tfCorrect3Pct = new BigDecimal("50");

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal tfCorrect4Pct = new BigDecimal("100");

    @PrePersist
    void prePersist() {
        if (examUuid == null) {
            examUuid = UuidV7Generator.generate();
        }
    }
}

