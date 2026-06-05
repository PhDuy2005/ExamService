package com.DoAn1.examservice.domain.requestDTO.exam;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.DoAn1.examservice.domain.enums.ExamStatus;
import com.DoAn1.examservice.domain.enums.ExamType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqCreateExamDTO {

    @NotBlank(message = "Exam name must not be blank")
    private String examName;

    @NotNull(message = "Grade id is required")
    private Long gradeId;

    @NotBlank(message = "School year must not be blank")
    private String schoolYear;

    @NotNull(message = "Exam type is required")
    private ExamType examType;

    private Instant startTime;

    private Instant endTime;

    @NotNull(message = "Duration minutes is required")
    @Min(value = 0, message = "Duration minutes must be at least 0")
    private Integer durationMinutes;

    @NotNull(message = "Total score is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Total score must be greater than 0")
    private BigDecimal totalScore;

    @NotNull(message = "Number of attempt is required")
    @Min(value = 0, message = "Number of attempt must be at least 0")
    private Integer numberOfAttempt;

    @NotNull(message = "Status is required")
    private ExamStatus status;

    @DecimalMin(value = "0.0", inclusive = true, message = "TF correct 1 percent must be non-negative")
    private BigDecimal tfCorrect1Pct;

    @DecimalMin(value = "0.0", inclusive = true, message = "TF correct 2 percent must be non-negative")
    private BigDecimal tfCorrect2Pct;

    @DecimalMin(value = "0.0", inclusive = true, message = "TF correct 3 percent must be non-negative")
    private BigDecimal tfCorrect3Pct;

    @DecimalMin(value = "0.0", inclusive = true, message = "TF correct 4 percent must be non-negative")
    private BigDecimal tfCorrect4Pct;

    @Valid
    private List<ReqExamQuestionDTO> examQuestions;

    @Valid
    private List<ReqExamQuestionGroupDTO> examQuestionGroups;
}
