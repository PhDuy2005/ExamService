package com.DoAn1.examservice.domain.responseDTO.omr;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.DoAn1.examservice.domain.enums.OmrScoringJobStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResOmrScoringJobDTO {
    private UUID jobUuid;
    private UUID examUuid;
    private String schoolYear;
    private OmrScoringJobStatus status;
    private Integer pageCount;
    private String rawImageUrl;
    private String scoredImageUrl;
    private Long resultCount;
    private Long completedCount;
    private Long failedCount;
    private List<ResOmrScoringJobResultDTO> results;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
