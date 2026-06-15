package com.DoAn1.examservice.domain.responseDTO.omr;

import java.math.BigDecimal;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.QuestionType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResExamPaperQuestionDTO {
    private Integer questionOrder;
    private Integer sectionQuestionNumber;
    private UUID questionUuid;
    private QuestionType questionType;
    private String imagePath;
    private String relativePath;
    private BigDecimal score;
    private Boolean fromQuestionGroup;
    private UUID groupUuid;
    private String groupName;
}
