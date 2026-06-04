package com.DoAn1.examservice.domain.requestDTO.questiongroup;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqUpdateQuestionGroupItemsDTO {

    @Valid
    @NotNull(message = "Group items are required")
    private List<ReqQuestionGroupItemDTO> items;
}
