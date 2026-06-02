package com.DoAn1.examservice.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.DoAn1.examservice.domain.responseDTO.omr.ResOmrScoringJobDTO;
import com.DoAn1.examservice.service.OmrScoringJobService;
import com.DoAn1.examservice.util.annotation.ApiMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
public class OmrScoringJobController {

    private final OmrScoringJobService omrScoringJobService;

    @PostMapping("/api/v1/omr/scoring-jobs")
    @ApiMessage("Create OMR scoring job")
    public ResponseEntity<ResOmrScoringJobDTO> createScoringJob(
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "examUuid") UUID examUuid) throws IOException {
        log.info("Create OMR scoring job request received: examUuid={}, originalFileName={}",
                examUuid, file != null ? file.getOriginalFilename() : null);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(omrScoringJobService.createScoringJob(file, examUuid));
    }

    @GetMapping("/api/v1/omr/scoring-jobs/{jobUuid}")
    @ApiMessage("Get OMR scoring job")
    public ResOmrScoringJobDTO getScoringJob(@PathVariable(name = "jobUuid") UUID jobUuid) {
        log.info("Get OMR scoring job request received: jobUuid={}", jobUuid);
        return omrScoringJobService.getScoringJob(jobUuid);
    }
}
