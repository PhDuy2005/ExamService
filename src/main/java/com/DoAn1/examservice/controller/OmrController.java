package com.DoAn1.examservice.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.DoAn1.examservice.service.OmrService.ExamPaperPdfFile;
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

    @GetMapping("/api/v1/omr/exams/{examUuid}/exam-papers/{paperCode}/download")
    @ApiMessage("Download OMR exam paper PDF")
    public ResponseEntity<Resource> downloadExamPaper(
            @PathVariable(name = "examUuid") UUID examUuid,
            @PathVariable(name = "paperCode") String paperCode) throws IOException {
        ExamPaperPdfFile pdfFile = omrService.getExamPaperPdfFile(examUuid, paperCode);
        Resource resource = new FileSystemResource(pdfFile.path());
        String filename = pdfFile.path().getFileName().toString();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(resource.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename)
                        .build()
                        .toString())
                .body(resource);
    }

    @PostMapping("/api/v1/omr/imports")
    @ApiMessage("Import OMR data")
    public ResOmrImportDTO importOmrData(@Valid @RequestBody ReqOmrImportDTO request) {
        return omrService.importOmrData(request);
    }
}
