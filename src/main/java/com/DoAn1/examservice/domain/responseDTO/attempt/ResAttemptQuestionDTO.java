package com.DoAn1.examservice.domain.responseDTO.attempt;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResAttemptQuestionDTO {
    private Integer questionOrder;
    private UUID questionUuid;
    private QuestionType questionType;
    private String questionContent;
    private String imagePath;
    private String relativePath;
    private String questionTopic;
    private BigDecimal score;
    private Boolean fromQuestionGroup;
    private UUID groupUuid;
    private String groupName;
    private List<ResAttemptQuestionMcOptionDTO> mcOptions;
    private List<ResAttemptQuestionTrueFalseStatementDTO> tfStatements;
    private String currentRawAnswer;
    private String currentNormalizedAnswer;
    private Integer answerChangeCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String correctAnswerRaw;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String correctNormalizedAnswer;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal earnedScore;
}
