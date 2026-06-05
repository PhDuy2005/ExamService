package com.DoAn1.examservice.controller;

import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.DoAn1.examservice.service.DashboardService;
import com.DoAn1.examservice.util.annotation.ApiMessage;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DashboardService dashboardService;

    @GetMapping("/api/v1/dashboard/exams/{examUuid}/results")
    @ApiMessage("Get exam results dashboard")
    public ResponseEntity<?> getExamResults(
            @PathVariable(name = "examUuid") UUID examUuid,
            @RequestParam(name = "exportXlsx", defaultValue = "false") boolean exportXlsx) {
        if (exportXlsx) {
            return buildXlsxResponse(
                    dashboardService.exportExamResults(examUuid),
                    "exam-results-" + examUuid + ".xlsx");
        }
        return ResponseEntity.ok(dashboardService.getExamResults(examUuid));
    }

    @GetMapping("/api/v1/dashboard/exams/{examUuid}/stats")
    @ApiMessage("Get exam stats dashboard")
    public ResponseEntity<?> getExamStats(
            @PathVariable(name = "examUuid") UUID examUuid,
            @RequestParam(name = "exportXlsx", defaultValue = "false") boolean exportXlsx) {
        if (exportXlsx) {
            return buildXlsxResponse(
                    dashboardService.exportExamStats(examUuid),
                    "exam-stats-" + examUuid + ".xlsx");
        }
        return ResponseEntity.ok(dashboardService.getExamStats(examUuid));
    }

    @GetMapping("/api/v1/dashboard/exams/{examUuid}/rankings")
    @ApiMessage("Get exam score ranking dashboard")
    public ResponseEntity<?> getExamRanking(
            @PathVariable(name = "examUuid") UUID examUuid,
            @RequestParam(name = "n", defaultValue = "10") int n,
            @RequestParam(name = "exportXlsx", defaultValue = "false") boolean exportXlsx) {
        if (exportXlsx) {
            return buildXlsxResponse(
                    dashboardService.exportExamRanking(examUuid, n),
                    "exam-ranking-" + examUuid + ".xlsx");
        }
        return ResponseEntity.ok(dashboardService.getExamRanking(examUuid, n));
    }

    private ResponseEntity<byte[]> buildXlsxResponse(byte[] content, String filename) {
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename)
                        .build()
                        .toString())
                .body(content);
    }
}
