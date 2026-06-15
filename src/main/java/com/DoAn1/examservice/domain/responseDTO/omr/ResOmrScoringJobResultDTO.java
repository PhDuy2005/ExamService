package com.DoAn1.examservice.domain.responseDTO.omr;

import java.math.BigDecimal;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.OmrScoringJobResultStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResOmrScoringJobResultDTO {
    private UUID jobResultUuid;
    private Integer pageNumber;
    private String paperCode;
    private String studentCode;
    private String schoolYear;
    private String studentFullname;
    private UUID studentUuid;
    private UUID attemptUuid;
    private OmrScoringJobResultStatus status;
    private BigDecimal score;
    private String rawImageUrl;
    private String rawImageRelativePath;
    private String scoredImageUrl;
    private String scoredImageRelativePath;
    private String errorMessage;
}
