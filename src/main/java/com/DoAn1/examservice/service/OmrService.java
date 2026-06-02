package com.DoAn1.examservice.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.DoAn1.examservice.domain.entity.Exam;
import com.DoAn1.examservice.domain.entity.ExamPaper;
import com.DoAn1.examservice.domain.entity.ExamQuestion;
import com.DoAn1.examservice.domain.entity.ExamQuestionGroup;
import com.DoAn1.examservice.domain.entity.ExamQuestionGroupItem;
import com.DoAn1.examservice.domain.entity.OmrImport;
import com.DoAn1.examservice.domain.enums.QuestionType;
import com.DoAn1.examservice.domain.requestDTO.omr.ReqCreateExamPaperDTO;
import com.DoAn1.examservice.domain.requestDTO.omr.ReqOmrAnswerDTO;
import com.DoAn1.examservice.domain.requestDTO.omr.ReqOmrImportDTO;
import com.DoAn1.examservice.domain.requestDTO.omr.ReqOmrSectionsDTO;
import com.DoAn1.examservice.domain.responseDTO.attempt.ResExamAttemptDTO;
import com.DoAn1.examservice.domain.responseDTO.omr.ResExamPaperDTO;
import com.DoAn1.examservice.domain.responseDTO.omr.ResExamPaperQuestionDTO;
import com.DoAn1.examservice.domain.responseDTO.omr.ResOmrImportDTO;
import com.DoAn1.examservice.exception.IdInvalidException;
import com.DoAn1.examservice.repository.ExamPaperRepository;
import com.DoAn1.examservice.repository.ExamQuestionGroupItemRepository;
import com.DoAn1.examservice.repository.ExamQuestionGroupRepository;
import com.DoAn1.examservice.repository.ExamQuestionRepository;
import com.DoAn1.examservice.repository.ExamRepository;
import com.DoAn1.examservice.repository.OmrImportRepository;
import com.DoAn1.examservice.util.SecurityUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OmrService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamQuestionGroupRepository examQuestionGroupRepository;
    private final ExamQuestionGroupItemRepository examQuestionGroupItemRepository;
    private final ExamPaperRepository examPaperRepository;
    private final OmrImportRepository omrImportRepository;
    private final ExamAttemptService examAttemptService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ResExamPaperDTO createExamPaper(ReqCreateExamPaperDTO request) {
        Exam exam = findExamById(request.getExamUuid());
        String paperCode = normalizePaperCode(request.getPaperCode());
        if (examPaperRepository.existsByExamUuidAndPaperCode(exam.getExamUuid(), paperCode)) {
            throw new IdInvalidException("Exam paper already exists with code: " + paperCode);
        }

        List<PaperQuestionSnapshot> snapshots = buildPaperQuestionSnapshots(exam.getExamUuid());
        if (snapshots.isEmpty()) {
            throw new IdInvalidException("Exam paper must contain at least one question");
        }

        ExamPaper examPaper = new ExamPaper();
        examPaper.setExamUuid(exam.getExamUuid());
        examPaper.setPaperCode(paperCode);
        examPaper.setQuestionSnapshotJson(serializeSnapshots(snapshots));
        examPaper.setGeneratedByUserUuid(resolveCurrentUserUuid());

        return buildPaperResponse(examPaperRepository.save(examPaper), snapshots);
    }

    @Transactional
    public ResOmrImportDTO importOmrData(ReqOmrImportDTO request) {
        String externalSubmissionId = normalizeExternalSubmissionId(request.getExternalSubmissionId());
        if (externalSubmissionId != null && omrImportRepository.existsByExternalSubmissionId(externalSubmissionId)) {
            throw new IdInvalidException("OMR submission already imported: " + externalSubmissionId);
        }

        String paperCode = normalizePaperCode(request.getPaperCode());
        ExamPaper paper = examPaperRepository.findByExamUuidAndPaperCode(request.getExamUuid(), paperCode)
                .orElseThrow(() -> new IdInvalidException("Exam paper not found with code: " + paperCode));

        List<PaperQuestionSnapshot> snapshots = deserializeSnapshots(paper.getQuestionSnapshotJson());
        Map<Integer, String> rawAnswerByQuestionOrder = buildRawAnswerMap(request.getSections(), snapshots);
        ResExamAttemptDTO attempt = examAttemptService.importOmrAttempt(
                request.getExamUuid(),
                request.getStudentUuid(),
                request.getStudentId(),
                request.getStudentFullname(),
                paper.getQuestionSnapshotJson(),
                rawAnswerByQuestionOrder,
                request.getRawImageUrl(),
                request.getScoredImageUrl());

        OmrImport omrImport = new OmrImport();
        omrImport.setExamUuid(request.getExamUuid());
        omrImport.setPaperUuid(paper.getPaperUuid());
        omrImport.setPaperCode(paper.getPaperCode());
        omrImport.setStudentUuid(request.getStudentUuid());
        omrImport.setAttemptUuid(attempt.getAttemptUuid());
        omrImport.setExternalSubmissionId(externalSubmissionId);
        omrImport.setScannedAt(request.getScannedAt());
        omrImport.setRawPayloadJson(serializePayload(request));
        omrImport.setStatus("IMPORTED");
        OmrImport savedImport = omrImportRepository.save(omrImport);

        return buildImportResponse(savedImport, attempt.getScore());
    }

    private Exam findExamById(UUID examUuid) {
        return examRepository.findByExamUuid(examUuid)
                .orElseThrow(() -> new IdInvalidException("Exam not found with id: " + examUuid));
    }

    private List<PaperQuestionSnapshot> buildPaperQuestionSnapshots(UUID examUuid) {
        List<PaperQuestionSnapshot> snapshots = new ArrayList<>();
        int nextOrder = 1;
        Map<QuestionType, Integer> nextSectionNumberByType = new LinkedHashMap<>();
        for (QuestionType questionType : QuestionType.values()) {
            nextSectionNumberByType.put(questionType, 1);
        }

        List<ExamQuestion> standaloneQuestions = examQuestionRepository.findByExamUuidOrderByQuestionOrderAsc(examUuid);
        for (ExamQuestion examQuestion : standaloneQuestions) {
            QuestionType questionType = examQuestion.getSectionType();
            snapshots.add(new PaperQuestionSnapshot(
                    nextOrder++,
                    nextSectionNumberByType.compute(questionType, (key, value) -> value + 1) - 1,
                    examQuestion.getQuestionUuid(),
                    questionType,
                    examQuestion.getScore(),
                    false,
                    null,
                    null));
        }

        for (ExamQuestionGroup group : examQuestionGroupRepository.findByExamUuidOrderByDisplayOrderAsc(examUuid)) {
            List<ExamQuestionGroupItem> poolItems = new ArrayList<>(examQuestionGroupItemRepository.findByEqgUuid(group.getEqgUuid()));
            Collections.shuffle(poolItems);
            List<ExamQuestionGroupItem> pickedItems = poolItems.stream()
                    .limit(group.getPickQuestionCount())
                    .toList();

            for (ExamQuestionGroupItem item : pickedItems) {
                QuestionType questionType = group.getQuestionType();
                snapshots.add(new PaperQuestionSnapshot(
                        nextOrder++,
                        nextSectionNumberByType.compute(questionType, (key, value) -> value + 1) - 1,
                        item.getQuestionUuid(),
                        questionType,
                        group.getScorePerQuestion(),
                        true,
                        group.getEqgUuid(),
                        group.getGroupName()));
            }
        }

        return snapshots;
    }

    private Map<Integer, String> buildRawAnswerMap(
            ReqOmrSectionsDTO sections,
            List<PaperQuestionSnapshot> snapshots) {
        Map<QuestionType, Map<Integer, PaperQuestionSnapshot>> snapshotBySectionNumber = snapshots.stream()
                .collect(Collectors.groupingBy(
                        PaperQuestionSnapshot::questionType,
                        Collectors.toMap(PaperQuestionSnapshot::sectionQuestionNumber, snapshot -> snapshot)));
        Map<Integer, String> rawAnswerByQuestionOrder = new LinkedHashMap<>();
        addSectionAnswers(rawAnswerByQuestionOrder, snapshotBySectionNumber, QuestionType.MCQ, safeAnswers(sections.getMcq()));
        addSectionAnswers(rawAnswerByQuestionOrder, snapshotBySectionNumber, QuestionType.TFQ, safeAnswers(sections.getTfq()));
        addSectionAnswers(rawAnswerByQuestionOrder, snapshotBySectionNumber, QuestionType.SAQ, safeAnswers(sections.getSaq()));

        if (rawAnswerByQuestionOrder.isEmpty()) {
            throw new IdInvalidException("OMR sections must contain at least one answer");
        }
        return rawAnswerByQuestionOrder;
    }

    private void addSectionAnswers(
            Map<Integer, String> rawAnswerByQuestionOrder,
            Map<QuestionType, Map<Integer, PaperQuestionSnapshot>> snapshotBySectionNumber,
            QuestionType expectedQuestionType,
            List<ReqOmrAnswerDTO> answers) {
        Map<Integer, PaperQuestionSnapshot> snapshotByNumber = snapshotBySectionNumber.getOrDefault(expectedQuestionType, Map.of());
        for (ReqOmrAnswerDTO answer : answers) {
            PaperQuestionSnapshot snapshot = snapshotByNumber.get(answer.getSectionQuestionNumber());
            if (snapshot == null) {
                continue;
            }
            if (rawAnswerByQuestionOrder.containsKey(snapshot.questionOrder())) {
                throw new IdInvalidException("Section question number must be unique in OMR section "
                        + expectedQuestionType + ": " + answer.getSectionQuestionNumber());
            }
            rawAnswerByQuestionOrder.put(snapshot.questionOrder(), answer.getRawAnswer());
        }
    }

    private List<ReqOmrAnswerDTO> safeAnswers(List<ReqOmrAnswerDTO> answers) {
        return answers == null ? List.of() : answers;
    }

    private ResExamPaperDTO buildPaperResponse(ExamPaper paper, List<PaperQuestionSnapshot> snapshots) {
        return ResExamPaperDTO.builder()
                .paperUuid(paper.getPaperUuid())
                .examUuid(paper.getExamUuid())
                .paperCode(paper.getPaperCode())
                .generatedAt(paper.getGeneratedAt())
                .generatedByUserUuid(paper.getGeneratedByUserUuid())
                .questions(snapshots.stream()
                        .sorted(Comparator.comparing(PaperQuestionSnapshot::questionOrder))
                        .map(snapshot -> ResExamPaperQuestionDTO.builder()
                                .questionOrder(snapshot.questionOrder())
                                .sectionQuestionNumber(snapshot.sectionQuestionNumber())
                                .questionUuid(snapshot.questionUuid())
                                .questionType(snapshot.questionType())
                                .score(snapshot.score())
                                .fromQuestionGroup(snapshot.fromQuestionGroup())
                                .groupUuid(snapshot.groupUuid())
                                .groupName(snapshot.groupName())
                                .build())
                        .toList())
                .build();
    }

    private ResOmrImportDTO buildImportResponse(OmrImport omrImport, BigDecimal score) {
        return ResOmrImportDTO.builder()
                .omrImportUuid(omrImport.getOmrImportUuid())
                .examUuid(omrImport.getExamUuid())
                .paperUuid(omrImport.getPaperUuid())
                .paperCode(omrImport.getPaperCode())
                .studentUuid(omrImport.getStudentUuid())
                .attemptUuid(omrImport.getAttemptUuid())
                .externalSubmissionId(omrImport.getExternalSubmissionId())
                .status(omrImport.getStatus())
                .score(score)
                .importedAt(omrImport.getImportedAt())
                .build();
    }

    private String serializeSnapshots(List<PaperQuestionSnapshot> snapshots) {
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException ex) {
            throw new IdInvalidException("Failed to serialize exam paper question snapshot", ex);
        }
    }

    private List<PaperQuestionSnapshot> deserializeSnapshots(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, new TypeReference<List<PaperQuestionSnapshot>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IdInvalidException("Failed to read exam paper question snapshot", ex);
        }
    }

    private String serializePayload(ReqOmrImportDTO request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IdInvalidException("Failed to serialize OMR import payload", ex);
        }
    }

    private UUID resolveCurrentUserUuid() {
        return SecurityUtil.getCurrentUserUuid()
                .map(UUID::fromString)
                .orElseThrow(() -> new IdInvalidException("User id is missing from JWT"));
    }

    private String normalizePaperCode(String paperCode) {
        return paperCode.trim().toUpperCase();
    }

    private String normalizeExternalSubmissionId(String externalSubmissionId) {
        return StringUtils.hasText(externalSubmissionId) ? externalSubmissionId.trim() : null;
    }

    private record PaperQuestionSnapshot(
            Integer questionOrder,
            Integer sectionQuestionNumber,
            UUID questionUuid,
            QuestionType questionType,
            BigDecimal score,
            Boolean fromQuestionGroup,
            UUID groupUuid,
            String groupName) {
    }
}
