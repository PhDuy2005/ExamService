package com.DoAn1.examservice.domain.responseDTO.attempt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.AttemptStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResExamAttemptSummaryDTO {
    private UUID attemptUuid;
    private UUID examUuid;
    private String examName;
    private String studentId;
    private String studentFullname;
    private Integer attemptNo;
    private Instant startedAt;
    private Instant submittedAt;
    private Integer timeSpentSeconds;
    private AttemptStatus status;
    private BigDecimal score;
    private Boolean isAutoSubmitted;
    private Integer violationCount;
    private String rawImageUrl;
    private String scoredImageUrl;
}
