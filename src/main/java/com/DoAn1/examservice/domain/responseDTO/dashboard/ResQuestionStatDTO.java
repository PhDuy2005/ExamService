package com.DoAn1.examservice.domain.responseDTO.dashboard;

import java.util.Map;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.QuestionType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResQuestionStatDTO {
    private Integer questionOrder;
    private UUID questionUuid;
    private QuestionType questionType;
    private String questionContent;
    private String imagePath;
    private String correctAnswer;
    private Map<String, Long> answerCounts;
}
