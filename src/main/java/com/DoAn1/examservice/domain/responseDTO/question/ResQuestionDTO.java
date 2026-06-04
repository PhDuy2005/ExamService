package com.DoAn1.examservice.domain.responseDTO.question;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.QuestionType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResQuestionDTO {
    private UUID questionUuid;
    private Long gradeId;
    private String questionContent;
    private String imagePath;
    private String questionTopic;
    private QuestionType questionType;
    private UUID createdByUserUuid;
    private Boolean isActive;
    private String correctAnswerRaw;
    private String normalizedAnswer;
    private List<ResQuestionMcOptionDTO> mcOptions;
    private List<ResQuestionTrueFalseStatementDTO> tfStatements;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
}
