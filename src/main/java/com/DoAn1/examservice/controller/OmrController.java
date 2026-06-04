package com.DoAn1.examservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.DoAn1.examservice.domain.requestDTO.omr.ReqCreateExamPaperDTO;
import com.DoAn1.examservice.domain.requestDTO.omr.ReqOmrImportDTO;
import com.DoAn1.examservice.domain.responseDTO.omr.ResExamPaperDTO;
import com.DoAn1.examservice.domain.responseDTO.omr.ResOmrImportDTO;
import com.DoAn1.examservice.service.OmrService;
import com.DoAn1.examservice.util.annotation.ApiMessage;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class OmrController {

    private final OmrService omrService;

    @PostMapping("/api/v1/omr/exam-papers")
    @ApiMessage("Create OMR exam paper")
    public ResExamPaperDTO createExamPaper(@Valid @RequestBody ReqCreateExamPaperDTO request) {
        return omrService.createExamPaper(request);
    }

    @GetMapping("/api/v1/omr/exams/{examUuid}/exam-papers")
    @ApiMessage("Get OMR exam papers by exam id")
    public List<ResExamPaperDTO> getExamPapersByExamUuid(
            @PathVariable(name = "examUuid") UUID examUuid) {
        return omrService.getExamPapersByExamUuid(examUuid);
    }

    @PostMapping("/api/v1/omr/imports")
    @ApiMessage("Import OMR data")
    public ResOmrImportDTO importOmrData(@Valid @RequestBody ReqOmrImportDTO request) {
        return omrService.importOmrData(request);
    }
}
