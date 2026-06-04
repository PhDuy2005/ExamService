package com.DoAn1.examservice.domain.requestDTO.question;

import java.util.List;
import com.DoAn1.examservice.domain.enums.QuestionType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqCreateQuestionDTO {

    @NotNull(message = "Grade id is required")
    private Long gradeId;

    @NotBlank(message = "Question content must not be blank")
    private String questionContent;

    @Size(max = 1000, message = "Image path must not exceed 1000 characters")
    private String imagePath;

    private String questionTopic;

    @NotNull(message = "Question type is required")
    private QuestionType questionType;

    private Boolean isActive = true;

    @Valid
    private List<ReqQuestionMcOptionDTO> mcOptions;

    @Valid
    private List<ReqQuestionTrueFalseStatementDTO> tfStatements;

    @Valid
    @NotNull(message = "Answer key is required")
    private ReqQuestionAnswerKeyDTO answerKey;
}
