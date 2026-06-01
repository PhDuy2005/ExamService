package com.DoAn1.examservice.domain.responseDTO.exam;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.ExamStatus;
import com.DoAn1.examservice.domain.enums.ExamType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResExamDTO {
    private UUID examUuid;
    private String examName;
    private Long gradeId;
    private String schoolYear;
    private ExamType examType;
    private Instant startTime;
    private Instant endTime;
    private Integer durationMinutes;
    private BigDecimal totalScore;
    private Integer numberOfAttempt;
    private ExamStatus status;
    private UUID createdByUserUuid;
    private BigDecimal tfCorrect1Pct;
    private BigDecimal tfCorrect2Pct;
    private BigDecimal tfCorrect3Pct;
    private BigDecimal tfCorrect4Pct;
    private ResExamQuestionSummaryDTO questionSummary;
    private List<ResExamQuestionTypeSectionDTO> questionSections;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
}
