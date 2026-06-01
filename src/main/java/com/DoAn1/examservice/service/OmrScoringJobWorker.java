package com.DoAn1.examservice.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.DoAn1.examservice.domain.entity.OmrScoringJob;
import com.DoAn1.examservice.domain.entity.OmrScoringJobResult;
import com.DoAn1.examservice.domain.enums.OmrScoringJobResultStatus;
import com.DoAn1.examservice.domain.enums.OmrScoringJobStatus;
import com.DoAn1.examservice.domain.requestDTO.omr.ReqOmrAnswerDTO;
import com.DoAn1.examservice.domain.requestDTO.omr.ReqOmrImportDTO;
import com.DoAn1.examservice.domain.requestDTO.omr.ReqOmrSectionsDTO;
import com.DoAn1.examservice.domain.responseDTO.omr.ResOmrImportDTO;
import com.DoAn1.examservice.repository.OmrScoringJobRepository;
import com.DoAn1.examservice.repository.OmrScoringJobResultRepository;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import scoring.normal.v1.ScoringNormal;
import scoring.normal.v1.ScoringNormalServiceGrpc;

@Slf4j
@Service
public class OmrScoringJobWorker {

    @Value("${examservice.scoring-service.host:localhost}")
    private String scoringServiceHost;

    @Value("${examservice.scoring-service.port:50051}")
    private Integer scoringServicePort;

    private final OmrScoringJobRepository omrScoringJobRepository;
    private final OmrScoringJobResultRepository omrScoringJobResultRepository;
    private final OmrService omrService;

    public OmrScoringJobWorker(
            OmrScoringJobRepository omrScoringJobRepository,
            OmrScoringJobResultRepository omrScoringJobResultRepository,
            OmrService omrService) {
        this.omrScoringJobRepository = omrScoringJobRepository;
        this.omrScoringJobResultRepository = omrScoringJobResultRepository;
        this.omrService = omrService;
    }

    @Async
    public void processJob(UUID jobUuid) {
        OmrScoringJob job = omrScoringJobRepository.findById(jobUuid)
                .orElseThrow(() -> new IllegalArgumentException("OMR scoring job not found with id: " + jobUuid));

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(scoringServiceHost, scoringServicePort)
                .usePlaintext()
                .build();

        try {
            ScoringNormalServiceGrpc.ScoringNormalServiceBlockingStub stub =
                    ScoringNormalServiceGrpc.newBlockingStub(channel);
            ScoringNormal.ReadOmrRequest request = ScoringNormal.ReadOmrRequest.newBuilder()
                    .setRequestId(job.getJobUuid().toString())
                    .setExamUuid(job.getExamUuid().toString())
                    .setPdfUrl(job.getRawImageUrl())
                    .setScannedAt(Instant.now().toString())
                    .build();

            stub.readOmr(request).forEachRemaining(response -> handleResponse(job, response));
            job.setStatus(OmrScoringJobStatus.COMPLETED);
            omrScoringJobRepository.save(job);
        } catch (Exception ex) {
            log.error("Failed to process OMR scoring job {}", jobUuid, ex);
            job.setStatus(OmrScoringJobStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            omrScoringJobRepository.save(job);
        } finally {
            channel.shutdown();
        }
    }

    private void handleResponse(OmrScoringJob job, ScoringNormal.ReadOmrResponse response) {
        if (!response.getSuccess()) {
            saveFailedResult(job, response.getPageNumber(), response.getErrorMessage());
            return;
        }

        if (!response.hasData()) {
            saveFailedResult(job, response.getPageNumber(), "Scoring response data is missing");
            return;
        }

        try {
            ScoringNormal.ReadOmrPayload payload = response.getData();
            ReqOmrImportDTO request = buildImportRequest(job, response, payload);
            ResOmrImportDTO imported = omrService.importOmrData(request);

            OmrScoringJobResult result = new OmrScoringJobResult();
            result.setJobUuid(job.getJobUuid());
            result.setPageNumber(response.getPageNumber());
            result.setPaperCode(payload.getPaperCode());
            result.setStudentCode(payload.getStudentCode());
            result.setSchoolYear(job.getSchoolYear());
            result.setStudentUuid(request.getStudentUuid());
            result.setAttemptUuid(imported.getAttemptUuid());
            result.setStatus(OmrScoringJobResultStatus.COMPLETED);
            result.setScore(imported.getScore());
            result.setRawImageUrl(firstText(payload.getRawImageUrl(), job.getRawImageUrl()));
            result.setScoredImageUrl(payload.getScoredImageUrl());
            omrScoringJobResultRepository.save(result);

            if (hasText(payload.getScoredImageUrl())) {
                job.setScoredImageUrl(payload.getScoredImageUrl());
                omrScoringJobRepository.save(job);
            }
        } catch (Exception ex) {
            log.error("Failed to import OMR response for job {} page {}",
                    job.getJobUuid(), response.getPageNumber(), ex);
            saveFailedResult(job, response, response.getData(), ex.getMessage());
        }
    }

    private ReqOmrImportDTO buildImportRequest(
            OmrScoringJob job,
            ScoringNormal.ReadOmrResponse response,
            ScoringNormal.ReadOmrPayload payload) {
        ReqOmrImportDTO request = new ReqOmrImportDTO();
        request.setExamUuid(job.getExamUuid());
        request.setPaperCode(payload.getPaperCode());
        request.setStudentUuid(resolveStudentUuid(payload.getStudentCode(), job.getSchoolYear()));
        request.setExternalSubmissionId(firstText(
                payload.getExternalSubmissionId(),
                job.getJobUuid() + "-page-" + response.getPageNumber()));
        request.setRawImageUrl(firstText(payload.getRawImageUrl(), job.getRawImageUrl()));
        request.setScoredImageUrl(payload.getScoredImageUrl());
        request.setScannedAt(parseInstant(payload.getScannedAt()));
        request.setSections(buildSections(payload.getSections()));
        return request;
    }

    private ReqOmrSectionsDTO buildSections(ScoringNormal.OmrSections sections) {
        ReqOmrSectionsDTO dto = new ReqOmrSectionsDTO();
        dto.setMcq(toAnswers(sections.getMcqList()));
        dto.setTfq(toAnswers(sections.getTfqList()));
        dto.setSaq(toAnswers(sections.getSaqList()));
        return dto;
    }

    private List<ReqOmrAnswerDTO> toAnswers(List<ScoringNormal.SectionAnswer> answers) {
        return answers.stream()
                .map(answer -> {
                    ReqOmrAnswerDTO dto = new ReqOmrAnswerDTO();
                    dto.setSectionQuestionNumber(answer.getSectionQuestionNumber());
                    dto.setRawAnswer(answer.getRawAnswer());
                    return dto;
                })
                .toList();
    }

    private void saveFailedResult(OmrScoringJob job, Integer pageNumber, String errorMessage) {
        OmrScoringJobResult result = new OmrScoringJobResult();
        result.setJobUuid(job.getJobUuid());
        result.setPageNumber(pageNumber);
        result.setStatus(OmrScoringJobResultStatus.FAILED);
        result.setRawImageUrl(job.getRawImageUrl());
        result.setErrorMessage(errorMessage);
        omrScoringJobResultRepository.save(result);
    }

    private void saveFailedResult(
            OmrScoringJob job,
            ScoringNormal.ReadOmrResponse response,
            ScoringNormal.ReadOmrPayload payload,
            String errorMessage) {
        OmrScoringJobResult result = new OmrScoringJobResult();
        result.setJobUuid(job.getJobUuid());
        result.setPageNumber(response.getPageNumber());
        result.setPaperCode(payload.getPaperCode());
        result.setStudentCode(payload.getStudentCode());
        result.setSchoolYear(job.getSchoolYear());
        result.setStatus(OmrScoringJobResultStatus.FAILED);
        result.setRawImageUrl(firstText(payload.getRawImageUrl(), job.getRawImageUrl()));
        result.setScoredImageUrl(payload.getScoredImageUrl());
        result.setErrorMessage(errorMessage);
        omrScoringJobResultRepository.save(result);
    }

    private UUID resolveStudentUuid(String studentCode, String schoolYear) {
        if (!hasText(studentCode)) {
            throw new IllegalArgumentException("Student code is required");
        }
        if (!hasText(schoolYear)) {
            throw new IllegalArgumentException("School year is required");
        }

        // TODO: Call Management Service gRPC to resolve studentCode + schoolYear -> userUuid.
        // ES must not generate a fallback UUID here. If MS cannot resolve the user,
        // this OMR result must be marked as FAILED and no ExamAttempt should be created.
        throw new UnsupportedOperationException("Resolve studentCode + schoolYear to userUuid via Management Service gRPC is not implemented");
    }

    private Instant parseInstant(String value) {
        if (!hasText(value)) {
            return Instant.now();
        }
        return Instant.parse(value);
    }

    private String firstText(String first, String fallback) {
        return hasText(first) ? first : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

