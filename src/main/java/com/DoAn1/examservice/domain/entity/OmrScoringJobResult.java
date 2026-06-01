package com.DoAn1.examservice.domain.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.OmrScoringJobResultStatus;
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
@Table(name = "omr_scoring_job_result", indexes = {
        @Index(name = "idx_omr_scoring_job_result_job", columnList = "jobUuid"),
        @Index(name = "idx_omr_scoring_job_result_attempt", columnList = "attemptUuid")
})
public class OmrScoringJobResult {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID jobResultUuid;

    @Column(nullable = false)
    private UUID jobUuid;

    private Integer pageNumber;

    @Column(length = 50)
    private String paperCode;

    @Column(length = 20)
    private String studentCode;

    @Column(length = 20)
    private String schoolYear;

    private UUID studentUuid;

    private UUID attemptUuid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OmrScoringJobResultStatus status;

    @Column(precision = 10, scale = 2)
    private BigDecimal score;

    @Column(length = 1000)
    private String rawImageUrl;

    @Column(length = 1000)
    private String scoredImageUrl;

    @Column(columnDefinition = "TEXT")
    private String extractedPayloadJson;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    void prePersist() {
        if (jobResultUuid == null) {
            jobResultUuid = UuidV7Generator.generate();
        }
    }
}
