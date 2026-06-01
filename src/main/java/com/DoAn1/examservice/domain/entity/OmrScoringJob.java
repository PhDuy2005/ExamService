package com.DoAn1.examservice.domain.entity;

import java.util.UUID;

import com.DoAn1.examservice.domain.enums.OmrScoringJobStatus;
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
@Table(name = "omr_scoring_job", indexes = {
        @Index(name = "idx_omr_scoring_job_exam", columnList = "examUuid"),
        @Index(name = "idx_omr_scoring_job_status", columnList = "status")
})
public class OmrScoringJob extends AuditableEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID jobUuid;

    @Column(nullable = false)
    private UUID examUuid;

    @Column(length = 20)
    private String schoolYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OmrScoringJobStatus status;

    @Column(nullable = false)
    private Integer pageCount;

    @Column(nullable = false, length = 1000)
    private String rawImageUrl;

    @Column(length = 1000)
    private String scoredImageUrl;

    @Column(columnDefinition = "TEXT")
    private String resultPayloadJson;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    void prePersist() {
        if (jobUuid == null) {
            jobUuid = UuidV7Generator.generate();
        }
    }
}
