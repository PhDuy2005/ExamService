package com.DoAn1.examservice.domain.responseDTO.dashboard;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.QuestionType;
import com.DoAn1.examservice.domain.enums.SubmitSource;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResStudentExamResultDTO {
    private String studentId;
    private String fullname;
    private UUID userUuid;
    private SubmitSource submitSource;
    private String paperCode;
    private BigDecimal totalScore;
    private Integer violationCount;
    private Map<QuestionType, BigDecimal> sectionScores;
}
