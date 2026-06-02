package com.DoAn1.examservice.domain.responseDTO.dashboard;

import java.math.BigDecimal;
import java.util.List;

import com.DoAn1.examservice.domain.enums.QuestionType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResSectionStatDTO {
    private QuestionType sectionType;
    private BigDecimal averageScore;
    private BigDecimal meanScore;
    private BigDecimal standardDeviationScore;
    private List<ResQuestionStatDTO> questions;
}
