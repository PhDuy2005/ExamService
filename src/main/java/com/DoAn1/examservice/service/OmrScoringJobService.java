package com.DoAn1.examservice.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.DoAn1.examservice.domain.entity.Exam;
import com.DoAn1.examservice.domain.entity.OmrScoringJobResult;
import com.DoAn1.examservice.domain.entity.OmrScoringJob;
import com.DoAn1.examservice.domain.enums.OmrScoringJobResultStatus;
import com.DoAn1.examservice.domain.enums.OmrScoringJobStatus;
import com.DoAn1.examservice.domain.responseDTO.omr.ResOmrScoringJobResultDTO;
import com.DoAn1.examservice.domain.responseDTO.omr.ResOmrScoringJobDTO;
import com.DoAn1.examservice.exception.IdInvalidException;
import com.DoAn1.examservice.exception.StorageException;
import com.DoAn1.examservice.repository.ExamRepository;
import com.DoAn1.examservice.repository.OmrScoringJobResultRepository;
import com.DoAn1.examservice.repository.OmrScoringJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class OmrScoringJobService {

    private static final String RAW_OMR_FOLDER = "omr/raw";
    private static final Pattern PDF_PAGE_PATTERN = Pattern.compile("/Type\\s*/Page(?!s)");

    private final ExamRepository examRepository;
    private final OmrScoringJobRepository omrScoringJobRepository;
    private final OmrScoringJobResultRepository omrScoringJobResultRepository;
    private final FileService fileService;
    private final OmrScoringJobWorker omrScoringJobWorker;

    @Transactional
    public ResOmrScoringJobDTO createScoringJob(
            MultipartFile file,
            UUID examUuid) throws IOException {
        log.info("Creating OMR scoring job: examUuid={}, originalFileName={}",
                examUuid, file != null ? file.getOriginalFilename() : null);
        validatePdfFile(file);
        if (examUuid == null) {
            log.warn("Create OMR scoring job rejected because examUuid is missing");
            throw new StorageException("Exam id is required");
        }
        log.debug("Resolving exam entity for OMR scoring job: examUuid={}", examUuid);
        Exam exam = examRepository.findById(examUuid)
                .orElseThrow(() -> new IdInvalidException("Exam not found with id: " + examUuid));
        if (!StringUtils.hasText(exam.getSchoolYear())) {
            log.warn("Create OMR scoring job rejected because schoolYear is missing: examUuid={}", examUuid);
            throw new IdInvalidException("Exam school year is required for OMR scoring job");
        }

        int pageCount = countPdfPages(file);
        String uploadedFileName = fileService.store(file, RAW_OMR_FOLDER);
        String rawImageUrl = fileService.buildStorageUrl(RAW_OMR_FOLDER, uploadedFileName);

        OmrScoringJob job = new OmrScoringJob();
        job.setExamUuid(examUuid);
        job.setSchoolYear(exam.getSchoolYear());
        job.setStatus(OmrScoringJobStatus.PROCESSING);
        job.setPageCount(pageCount);
        job.setRawImageUrl(rawImageUrl);

        OmrScoringJob savedJob = omrScoringJobRepository.save(job);
        omrScoringJobWorker.processJob(savedJob.getJobUuid());
        log.info("OMR scoring job created successfully: jobUuid={}, examUuid={}, pageCount={}, status={}",
                savedJob.getJobUuid(), savedJob.getExamUuid(), savedJob.getPageCount(), savedJob.getStatus());
        return buildResponse(savedJob);
    }

    @Transactional(readOnly = true)
    public ResOmrScoringJobDTO getScoringJob(UUID jobUuid) {
        log.info("Getting OMR scoring job: jobUuid={}", jobUuid);
        log.debug("Resolving OMR scoring job entity: jobUuid={}", jobUuid);
        OmrScoringJob job = omrScoringJobRepository.findByJobUuid(jobUuid)
                .orElseThrow(() -> new IdInvalidException("OMR scoring job not found with id: " + jobUuid));
        log.info("OMR scoring job retrieved successfully: jobUuid={}, examUuid={}, status={}",
                job.getJobUuid(), job.getExamUuid(), job.getStatus());
        return buildResponse(job);
    }

    private void validatePdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("Create OMR scoring job rejected because PDF file is missing or empty");
            throw new StorageException("PDF file is required");
        }

        String fileName = file.getOriginalFilename();
        boolean isPdfExtension = fileName != null && fileName.toLowerCase().endsWith(".pdf");
        if (!isPdfExtension) {
            log.warn("Create OMR scoring job rejected because file extension is invalid: originalFileName={}",
                    fileName);
            throw new StorageException("Only PDF file is allowed");
        }

        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && !"application/pdf".equals(contentType)) {
            log.warn("Create OMR scoring job rejected because MIME type is invalid: originalFileName={}, contentType={}",
                    fileName, contentType);
            throw new StorageException("Invalid file type based on MIME type. Only application/pdf is allowed");
        }
    }

    private int countPdfPages(MultipartFile file) throws IOException {
        log.debug("Counting PDF pages for OMR scoring job: originalFileName={}", file.getOriginalFilename());
        String pdfContent = new String(file.getBytes(), StandardCharsets.ISO_8859_1);
        Matcher matcher = PDF_PAGE_PATTERN.matcher(pdfContent);
        int pageCount = 0;
        while (matcher.find()) {
            pageCount++;
        }
        if (pageCount <= 0) {
            log.warn("Create OMR scoring job rejected because PDF page count could not be determined: originalFileName={}",
                    file.getOriginalFilename());
            throw new StorageException("Cannot read PDF page count");
        }
        return pageCount;
    }

    private ResOmrScoringJobDTO buildResponse(OmrScoringJob job) {
        log.debug("Building OMR scoring job response: jobUuid={}", job.getJobUuid());
        List<OmrScoringJobResult> results = omrScoringJobResultRepository
                .findByJobUuidOrderByPageNumberAsc(job.getJobUuid());
        return ResOmrScoringJobDTO.builder()
                .jobUuid(job.getJobUuid())
                .examUuid(job.getExamUuid())
                .schoolYear(job.getSchoolYear())
                .status(job.getStatus())
                .pageCount(job.getPageCount())
                .rawImageUrl(job.getRawImageUrl())
                .scoredImageUrl(job.getScoredImageUrl())
                .resultCount(omrScoringJobResultRepository.countByJobUuid(job.getJobUuid()))
                .completedCount(omrScoringJobResultRepository.countByJobUuidAndStatus(
                        job.getJobUuid(), OmrScoringJobResultStatus.COMPLETED))
                .failedCount(omrScoringJobResultRepository.countByJobUuidAndStatus(
                        job.getJobUuid(), OmrScoringJobResultStatus.FAILED))
                .results(results.stream()
                        .map(this::buildResultResponse)
                        .toList())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private ResOmrScoringJobResultDTO buildResultResponse(OmrScoringJobResult result) {
        return ResOmrScoringJobResultDTO.builder()
                .jobResultUuid(result.getJobResultUuid())
                .pageNumber(result.getPageNumber())
                .paperCode(result.getPaperCode())
                .studentCode(result.getStudentCode())
                .schoolYear(result.getSchoolYear())
                .studentUuid(result.getStudentUuid())
                .attemptUuid(result.getAttemptUuid())
                .status(result.getStatus())
                .score(result.getScore())
                .rawImageUrl(result.getRawImageUrl())
                .scoredImageUrl(result.getScoredImageUrl())
                .errorMessage(result.getErrorMessage())
                .build();
    }
}
