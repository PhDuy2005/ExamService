package com.DoAn1.examservice.domain.responseDTO.dashboard;

import java.util.List;

import com.DoAn1.examservice.domain.enums.SubmitSource;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResRankingGroupDTO {
    private String paperCode;
    private SubmitSource submitSource;
    private List<ResStudentRankingDTO> students;
}
