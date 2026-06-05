package com.DoAn1.examservice.domain.responseDTO.questiongroup;

import java.util.UUID;

import com.DoAn1.examservice.domain.enums.QuestionType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResQuestionGroupQuestionDetailDTO {
    private UUID questionUuid;
    private String questionContent;
    private String imagePath;
    private String questionTopic;
    private QuestionType questionType;
}
