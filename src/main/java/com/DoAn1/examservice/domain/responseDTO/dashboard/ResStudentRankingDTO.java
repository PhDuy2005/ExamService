package com.DoAn1.examservice.domain.responseDTO.dashboard;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResStudentRankingDTO {
    private Integer rank;
    private String studentId;
    private String fullname;
    private UUID userUuid;
    private BigDecimal score;
}
