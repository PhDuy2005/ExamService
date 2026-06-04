package com.DoAn1.examservice.domain.responseDTO.omr;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResExamPaperDTO {
    private UUID paperUuid;
    private UUID examUuid;
    private String paperCode;
    private Instant generatedAt;
    private UUID generatedByUserUuid;
    private String pdfUrl;
    private List<ResExamPaperQuestionDTO> questions;
}
