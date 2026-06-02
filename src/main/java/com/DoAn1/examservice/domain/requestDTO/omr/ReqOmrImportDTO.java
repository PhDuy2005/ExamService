package com.DoAn1.examservice.domain.requestDTO.omr;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqOmrImportDTO {

    @NotNull(message = "Exam id is required")
    private UUID examUuid;

    @NotBlank(message = "Paper code must not be blank")
    private String paperCode;

    @NotNull(message = "Student id is required")
    private UUID studentUuid;

    private String studentId;

    private String studentFullname;

    private String externalSubmissionId;

    private String rawImageUrl;

    private String scoredImageUrl;

    private Instant scannedAt;

    @Valid
    @NotNull(message = "Sections are required")
    private ReqOmrSectionsDTO sections;
}
