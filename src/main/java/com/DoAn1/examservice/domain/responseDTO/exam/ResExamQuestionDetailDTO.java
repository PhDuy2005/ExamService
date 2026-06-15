package com.DoAn1.examservice.domain.responseDTO.exam;

import java.util.UUID;

import com.DoAn1.examservice.domain.enums.QuestionType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResExamQuestionDetailDTO {
    private UUID questionUuid;
    private String questionContent;
    private String imagePath;
    private String relativePath;
    private String questionTopic;
    private QuestionType questionType;
}
