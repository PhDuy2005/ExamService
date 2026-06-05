package com.DoAn1.examservice.domain.responseDTO.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResExamResultDashboardDTO {
    private UUID examUuid;
    private String schoolYear;
    private String examName;
    private Instant startTime;
    private Instant endTime;
    private String createdBy;
    private List<ResStudentExamResultDTO> students;
}
