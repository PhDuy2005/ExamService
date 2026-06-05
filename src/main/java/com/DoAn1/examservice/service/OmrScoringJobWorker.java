package com.DoAn1.examservice.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
import com.DoAn1.examservice.service.event.OmrScoringJobCreatedEvent;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import management.v1.StudentResolver;
import management.v1.StudentResolverServiceGrpc;
import scoring.normal.v1.ScoringNormal;
import scoring.normal.v1.ScoringNormalServiceGrpc;

@Slf4j
@Service
public class OmrScoringJobWorker {

    @Value("${examservice.scoring-service.host:localhost}")
    private String scoringServiceHost;

    @Value("${examservice.scoring-service.port:50051}")
    private Integer scoringServicePort;

    @Value("${examservice.scoring-service.timeout-seconds:90}")
    private Long scoringServiceTimeoutSeconds;

    @Value("${examservice.management-service.host:localhost}")
    private String managementServiceHost;

    @Value("${examservice.management-service.port:9092}")
    private Integer managementServicePort;

    @Value("${examservice.management-service.timeout-seconds:20}")
    private Long managementServiceTimeoutSeconds;

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
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleJobCreated(OmrScoringJobCreatedEvent event) {
        processJob(event.jobUuid());
    }

    public void processJob(UUID jobUuid) {
        log.info("Processing OMR scoring job: jobUuid={}", jobUuid);
        OmrScoringJob job = omrScoringJobRepository.findByJobUuid(jobUuid)
                .orElseThrow(() -> new IllegalArgumentException("OMR scoring job not found with id: " + jobUuid));

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(scoringServiceHost, scoringServicePort)
                .usePlaintext()
                .build();

        try {
            ScoringNormalServiceGrpc.ScoringNormalServiceBlockingStub stub = ScoringNormalServiceGrpc
                    .newBlockingStub(channel)
                    .withDeadlineAfter(scoringServiceTimeoutSeconds, TimeUnit.SECONDS);
            ScoringNormal.ReadOmrRequest request = ScoringNormal.ReadOmrRequest.newBuilder()
                    .setRequestId(job.getJobUuid().toString())
                    .setExamUuid(job.getExamUuid().toString())
                    .setPdfUrl(job.getRawImageUrl())
                    .setScannedAt(Instant.now().toString())
                    .build();

            log.debug("Calling scoring service for OMR scoring job: jobUuid={}, examUuid={}, pageCount={}",
                    job.getJobUuid(), job.getExamUuid(), job.getPageCount());
            stub.readOmr(request).forEachRemaining(response -> handleResponse(job, response));
            job.setStatus(OmrScoringJobStatus.COMPLETED);
            omrScoringJobRepository.save(job);
            log.info("OMR scoring job processed successfully: jobUuid={}, examUuid={}, status={}",
                    job.getJobUuid(), job.getExamUuid(), job.getStatus());
        } catch (StatusRuntimeException ex) {
            String errorMessage = buildGrpcErrorMessage("Scoring service", ex);
            log.error("Processing OMR scoring job failed by gRPC error: jobUuid={}, examUuid={}, code={}, message={}",
                    jobUuid, job.getExamUuid(), ex.getStatus().getCode(), errorMessage, ex);
            job.setStatus(OmrScoringJobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            omrScoringJobRepository.save(job);
        } catch (Exception ex) {
            log.error("Processing OMR scoring job failed: jobUuid={}, examUuid={}, message={}",
                    jobUuid, job.getExamUuid(), ex.getMessage(), ex);
            job.setStatus(OmrScoringJobStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            omrScoringJobRepository.save(job);
        } finally {
            channel.shutdown();
        }
    }

    private void handleResponse(OmrScoringJob job, ScoringNormal.ReadOmrResponse response) {
        if (!response.getSuccess()) {
            log.warn("OMR scoring result rejected by scoring service: jobUuid={}, pageNumber={}, reason={}",
                    job.getJobUuid(), response.getPageNumber(), response.getErrorMessage());
            saveFailedResult(job, response.getPageNumber(), response.getErrorMessage());
            return;
        }

        if (!response.hasData()) {
            log.warn("OMR scoring result rejected because payload is missing: jobUuid={}, pageNumber={}",
                    job.getJobUuid(), response.getPageNumber());
            saveFailedResult(job, response.getPageNumber(), "Scoring response data is missing");
            return;
        }

        try {
            ScoringNormal.ReadOmrPayload payload = response.getData();
            OmrImportRequestContext requestContext = buildImportRequest(job, response, payload);
            ResOmrImportDTO imported = omrService.importOmrData(requestContext.request());

            OmrScoringJobResult result = new OmrScoringJobResult();
            result.setJobUuid(job.getJobUuid());
            result.setPageNumber(response.getPageNumber());
            result.setPaperCode(payload.getPaperCode());
            result.setStudentCode(payload.getStudentCode());
            result.setSchoolYear(job.getSchoolYear());
            result.setStudentFullname(requestContext.studentFullname());
            result.setStudentUuid(requestContext.request().getStudentUuid());
            result.setAttemptUuid(imported.getAttemptUuid());
            result.setStatus(OmrScoringJobResultStatus.COMPLETED);
            result.setScore(imported.getScore());
            result.setRawImageUrl(firstText(payload.getRawImageUrl(), job.getRawImageUrl()));
            result.setScoredImageUrl(payload.getScoredImageUrl());
            omrScoringJobResultRepository.save(result);
            log.debug(
                    "OMR scoring result imported successfully: jobUuid={}, pageNumber={}, studentCode={}, attemptUuid={}",
                    job.getJobUuid(), response.getPageNumber(), payload.getStudentCode(), imported.getAttemptUuid());

            if (hasText(payload.getScoredImageUrl())) {
                job.setScoredImageUrl(payload.getScoredImageUrl());
                omrScoringJobRepository.save(job);
            }
        } catch (Exception ex) {
            log.error("Importing OMR scoring result failed: jobUuid={}, pageNumber={}, message={}",
                    job.getJobUuid(), response.getPageNumber(), ex.getMessage(), ex);
            saveFailedResult(job, response, response.getData(), ex.getMessage());
        }
    }

    private OmrImportRequestContext buildImportRequest(
            OmrScoringJob job,
            ScoringNormal.ReadOmrResponse response,
            ScoringNormal.ReadOmrPayload payload) {
        ResolvedStudentIdentity studentIdentity = resolveStudentIdentity(payload.getStudentCode(), job.getSchoolYear());
        ReqOmrImportDTO request = new ReqOmrImportDTO();
        request.setExamUuid(job.getExamUuid());
        request.setPaperCode(payload.getPaperCode());
        request.setStudentUuid(studentIdentity.userUuid());
        request.setStudentId(studentIdentity.studentId());
        request.setStudentFullname(studentIdentity.fullname());
        request.setExternalSubmissionId(firstText(
                payload.getExternalSubmissionId(),
                job.getJobUuid() + "-page-" + response.getPageNumber()));
        request.setRawImageUrl(firstText(payload.getRawImageUrl(), job.getRawImageUrl()));
        request.setScoredImageUrl(payload.getScoredImageUrl());
        request.setScannedAt(parseInstant(payload.getScannedAt()));
        request.setSections(buildSections(payload.getSections()));
        return new OmrImportRequestContext(request, studentIdentity.fullname());
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

    private ResolvedStudentIdentity resolveStudentIdentity(String studentCode, String schoolYear) {
        if (!hasText(studentCode)) {
            throw new IllegalArgumentException("Student code is required");
        }
        if (!hasText(schoolYear)) {
            throw new IllegalArgumentException("School year is required");
        }

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(managementServiceHost, managementServicePort)
                .usePlaintext()
                .build();

        try {
            StudentResolverServiceGrpc.StudentResolverServiceBlockingStub stub = StudentResolverServiceGrpc
                    .newBlockingStub(channel)
                    .withDeadlineAfter(managementServiceTimeoutSeconds, TimeUnit.SECONDS);
            StudentResolver.ResolveStudentsRequest request = StudentResolver.ResolveStudentsRequest.newBuilder()
                    .setSchoolYear(schoolYear)
                    .addStudentIds(studentCode)
                    .build();
            StudentResolver.ResolveStudentsResponse response;
            try {
                response = stub.resolveStudents(request);
            } catch (StatusRuntimeException ex) {
                throw mapStudentResolverGrpcException(studentCode, ex);
            }

            if (response.getUnresolvedStudentIdsList().contains(studentCode)) {
                throw new IllegalArgumentException("Student code could not be resolved: " + studentCode);
            }

            return response.getStudentsList().stream()
                    .filter(student -> studentCode.equals(student.getStudentId()))
                    .findFirst()
                    .map(student -> new ResolvedStudentIdentity(
                            parseUserUuid(student),
                            student.getStudentId(),
                            student.getFullname()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Student code could not be resolved: " + studentCode));
        } finally {
            channel.shutdown();
        }
    }

    private RuntimeException mapStudentResolverGrpcException(String studentCode, StatusRuntimeException ex) {
        Status.Code code = ex.getStatus().getCode();
        String errorMessage = buildGrpcErrorMessage("Management service", ex);
        log.warn("Student resolver gRPC call failed: studentCode={}, code={}, message={}",
                studentCode, code, errorMessage, ex);

        if (code == Status.Code.INVALID_ARGUMENT || code == Status.Code.NOT_FOUND) {
            return new IllegalArgumentException(errorMessage, ex);
        }

        if (code == Status.Code.UNKNOWN) {
            return new IllegalStateException(errorMessage
                    + ". Management service may be throwing an unhandled server-side exception.", ex);
        }

        return new IllegalStateException(errorMessage, ex);
    }

    private String buildGrpcErrorMessage(String serviceName, StatusRuntimeException ex) {
        Status status = ex.getStatus();
        String description = hasText(status.getDescription())
                ? status.getDescription()
                : ex.getMessage();
        return serviceName + " gRPC failed with status " + status.getCode()
                + (hasText(description) ? ": " + description : "");
    }

    private UUID parseUserUuid(StudentResolver.ResolvedStudent student) {
        if (!hasText(student.getUserUuid())) {
            throw new IllegalArgumentException("Resolved student user uuid is missing: " + student.getStudentId());
        }
        return UUID.fromString(student.getUserUuid());
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

    private record OmrImportRequestContext(ReqOmrImportDTO request, String studentFullname) {
    }

    private record ResolvedStudentIdentity(UUID userUuid, String studentId, String fullname) {
    }
}
