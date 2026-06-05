package com.DoAn1.examservice.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.DoAn1.examservice.util.UuidV7Generator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "exam_paper", uniqueConstraints = {
        @UniqueConstraint(name = "uk_exam_paper_exam_code", columnNames = { "examUuid", "paperCode" })
}, indexes = {
        @Index(name = "idx_exam_paper_exam", columnList = "examUuid")
})
public class ExamPaper extends AuditableEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID paperUuid;

    @Column(nullable = false)
    private UUID examUuid;

    @Column(nullable = false, length = 50)
    private String paperCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionSnapshotJson;

    @Column(nullable = false)
    private Instant generatedAt;

    @Column(nullable = false)
    private UUID generatedByUserUuid;

    @Column(length = 1000)
    private String pdfUrl;

    @PrePersist
    void prePersist() {
        if (paperUuid == null) {
            paperUuid = UuidV7Generator.generate();
        }
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }
}
