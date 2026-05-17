package com.DoAn1.examservice.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.DoAn1.examservice.domain.entity.Exam;
import com.DoAn1.examservice.domain.entity.ExamQuestion;
import com.DoAn1.examservice.domain.entity.ExamQuestionGroup;
import com.DoAn1.examservice.domain.entity.ExamQuestionGroupItem;
import com.DoAn1.examservice.domain.entity.Question;
import com.DoAn1.examservice.domain.entity.QuestionGroup;
import com.DoAn1.examservice.domain.entity.QuestionGroupItem;
import com.DoAn1.examservice.domain.enums.ExamStatus;
import com.DoAn1.examservice.domain.enums.QuestionType;
import com.DoAn1.examservice.domain.requestDTO.exam.ReqCreateExamDTO;
import com.DoAn1.examservice.domain.requestDTO.exam.ReqExamQuestionDTO;
import com.DoAn1.examservice.domain.requestDTO.exam.ReqExamQuestionGroupDTO;
import com.DoAn1.examservice.domain.requestDTO.exam.ReqExamStatusDTO;
import com.DoAn1.examservice.domain.requestDTO.exam.ReqUpdateExamDTO;
import com.DoAn1.examservice.domain.responseDTO.exam.ResExamDTO;
import com.DoAn1.examservice.domain.responseDTO.exam.ResExamQuestionDetailDTO;
import com.DoAn1.examservice.domain.responseDTO.exam.ResExamQuestionGroupDTO;
import com.DoAn1.examservice.domain.responseDTO.exam.ResExamQuestionGroupItemDTO;
import com.DoAn1.examservice.domain.responseDTO.exam.ResExamQuestionSummaryDTO;
import com.DoAn1.examservice.domain.responseDTO.exam.ResExamQuestionTypeSectionDTO;
import com.DoAn1.examservice.domain.responseDTO.exam.ResExamStandaloneQuestionDTO;
import com.DoAn1.examservice.exception.IdInvalidException;
import com.DoAn1.examservice.repository.ExamQuestionGroupItemRepository;
import com.DoAn1.examservice.repository.ExamQuestionGroupRepository;
import com.DoAn1.examservice.repository.ExamQuestionRepository;
import com.DoAn1.examservice.repository.ExamRepository;
import com.DoAn1.examservice.repository.QuestionRepository;
import com.DoAn1.examservice.util.SecurityUtil;
import com.DoAn1.examservice.util.UuidV7Generator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExamService {

    private static final BigDecimal DEFAULT_TF_CORRECT_1_PCT = BigDecimal.TEN;
    private static final BigDecimal DEFAULT_TF_CORRECT_2_PCT = new BigDecimal("25");
    private static final BigDecimal DEFAULT_TF_CORRECT_3_PCT = new BigDecimal("50");
    private static final BigDecimal DEFAULT_TF_CORRECT_4_PCT = new BigDecimal("100");

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamQuestionGroupRepository examQuestionGroupRepository;
    private final ExamQuestionGroupItemRepository examQuestionGroupItemRepository;
    private final QuestionRepository questionRepository;
    private final QuestionGroupService questionGroupService;

    @Transactional
    public ResExamDTO createExam(ReqCreateExamDTO request) {
        ValidationContext validationContext = validateExamPayload(request);

        Exam exam = new Exam();
        applyExamData(exam, request);
        exam.setCreatedByUserUuid(resolveCurrentUserUuid());
        Exam savedExam = examRepository.save(exam);

        replaceExamDetails(savedExam.getExamUuid(), request, validationContext);
        return buildResponse(savedExam);
    }

    @Transactional(readOnly = true)
    public ResExamDTO getExam(UUID examUuid) {
        return buildResponse(findExamById(examUuid));
    }

    @Transactional(readOnly = true)
    public Page<ResExamDTO> getExams(Long gradeId, String name, String type, String status, Pageable pageable) {
        Specification<Exam> specification = (root, query, cb) -> cb.conjunction();

        if (gradeId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("gradeId"), gradeId));
        }
        if (StringUtils.hasText(name)) {
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("examName")), "%" + name.trim().toLowerCase() + "%"));
        }
        if (StringUtils.hasText(type)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("examType"),
                    parseExamType(type)));
        }
        if (StringUtils.hasText(status)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"),
                    parseExamStatus(status)));
        }

        return examRepository.findAll(specification, pageable)
                .map(this::buildResponse);
    }

    @Transactional
    public ResExamDTO updateExam(UUID examUuid, ReqUpdateExamDTO request) {
        ValidationContext validationContext = validateExamPayload(request);

        Exam exam = findExamById(examUuid);
        UUID createdByUserUuid = exam.getCreatedByUserUuid();
        applyExamData(exam, request);
        exam.setCreatedByUserUuid(createdByUserUuid);
        Exam savedExam = examRepository.save(exam);

        replaceExamDetails(savedExam.getExamUuid(), request, validationContext);
        return buildResponse(savedExam);
    }

    @Transactional
    public ResExamDTO updateExamStatus(UUID examUuid, ReqExamStatusDTO request) {
        Exam exam = findExamById(examUuid);
        exam.setStatus(request.getStatus());
        return buildResponse(examRepository.save(exam));
    }

    private Exam findExamById(UUID examUuid) {
        return examRepository.findById(examUuid)
                .orElseThrow(() -> new IdInvalidException("Exam not found with id: " + examUuid));
    }

    private void applyExamData(Exam exam, ReqCreateExamDTO request) {
        exam.setExamName(request.getExamName().trim());
        exam.setGradeId(request.getGradeId());
        exam.setExamType(request.getExamType());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setTotalScore(request.getTotalScore());
        exam.setNumberOfAttempt(request.getNumberOfAttempt());
        exam.setStatus(request.getStatus());
        exam.setTfCorrect1Pct(request.getTfCorrect1Pct() != null ? request.getTfCorrect1Pct() : DEFAULT_TF_CORRECT_1_PCT);
        exam.setTfCorrect2Pct(request.getTfCorrect2Pct() != null ? request.getTfCorrect2Pct() : DEFAULT_TF_CORRECT_2_PCT);
        exam.setTfCorrect3Pct(request.getTfCorrect3Pct() != null ? request.getTfCorrect3Pct() : DEFAULT_TF_CORRECT_3_PCT);
        exam.setTfCorrect4Pct(request.getTfCorrect4Pct() != null ? request.getTfCorrect4Pct() : DEFAULT_TF_CORRECT_4_PCT);
    }

    private ValidationContext validateExamPayload(ReqCreateExamDTO request) {
        if (request.getStartTime() != null && request.getEndTime() != null
                && !request.getEndTime().isAfter(request.getStartTime())) {
            throw new IdInvalidException("End time must be after start time");
        }

        validateTfPercentages(request);

        List<ReqExamQuestionDTO> examQuestions = request.getExamQuestions() == null
                ? List.of()
                : request.getExamQuestions();
        List<ReqExamQuestionGroupDTO> examQuestionGroups = request.getExamQuestionGroups() == null
                ? List.of()
                : request.getExamQuestionGroups();

        Set<Integer> questionOrders = examQuestions.stream()
                .map(ReqExamQuestionDTO::getQuestionOrder)
                .collect(Collectors.toSet());
        if (questionOrders.size() != examQuestions.size()) {
            throw new IdInvalidException("Question order must be unique within the exam");
        }

        Set<Integer> displayOrders = examQuestionGroups.stream()
                .map(ReqExamQuestionGroupDTO::getDisplayOrder)
                .collect(Collectors.toSet());
        if (displayOrders.size() != examQuestionGroups.size()) {
            throw new IdInvalidException("Display order must be unique within exam question groups");
        }

        List<ResolvedExamQuestionGroupPayload> resolvedGroups = resolveExamQuestionGroups(examQuestionGroups);

        Set<UUID> referencedQuestionIds = new java.util.LinkedHashSet<>();
        examQuestions.forEach(item -> referencedQuestionIds.add(item.getQuestionUuid()));
        resolvedGroups.forEach(group -> group.items().forEach(item -> referencedQuestionIds.add(item.getQuestionUuid())));

        Map<UUID, Question> questionById = referencedQuestionIds.isEmpty()
                ? Map.of()
                : questionRepository.findAllById(referencedQuestionIds).stream()
                        .collect(Collectors.toMap(Question::getQuestionUuid, Function.identity()));

        if (questionById.size() != referencedQuestionIds.size()) {
            Set<UUID> foundIds = questionById.keySet();
            UUID missingId = referencedQuestionIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .findFirst()
                    .orElse(null);
            throw new IdInvalidException("Question not found with id: " + missingId);
        }

        for (ReqExamQuestionDTO examQuestion : examQuestions) {
            Question question = questionById.get(examQuestion.getQuestionUuid());
            if (question.getQuestionType() != examQuestion.getSectionType()) {
                throw new IdInvalidException("Exam question section type must match question type for question id: "
                        + examQuestion.getQuestionUuid());
            }
        }

        for (ResolvedExamQuestionGroupPayload group : resolvedGroups) {
            if (group.pickQuestionCount().intValue() > group.questionGroup().getQuestionCount().intValue()) {
                throw new IdInvalidException("Pick question count must be less than or equal to pool size for group: "
                        + group.questionGroup().getGroupName());
            }

            for (QuestionGroupItem item : group.items()) {
                Question question = questionById.get(item.getQuestionUuid());
                if (question.getQuestionType() != group.questionGroup().getQuestionType()) {
                    throw new IdInvalidException("Group question type must match item question type for question id: "
                            + item.getQuestionUuid());
                }
                String groupTopic = normalizeTopic(group.questionGroup().getQuestionTopic());
                String questionTopic = normalizeTopic(question.getQuestionTopic());
                if (groupTopic != null && !groupTopic.equalsIgnoreCase(questionTopic)) {
                    throw new IdInvalidException("Group question topic must match item question topic for question id: "
                            + item.getQuestionUuid());
                }
            }
        }

        return new ValidationContext(questionById, resolvedGroups);
    }

    private List<ResolvedExamQuestionGroupPayload> resolveExamQuestionGroups(List<ReqExamQuestionGroupDTO> examQuestionGroups) {
        List<ResolvedExamQuestionGroupPayload> resolvedGroups = new ArrayList<>();

        for (ReqExamQuestionGroupDTO group : examQuestionGroups) {
            boolean hasExistingGroup = group.getQuestionGroupUuid() != null;
            boolean hasNewGroup = group.getNewQuestionGroup() != null;

            if (hasExistingGroup == hasNewGroup) {
                throw new IdInvalidException(
                        "Each exam question group must provide either questionGroupUuid or newQuestionGroup");
            }

            QuestionGroupService.QuestionGroupResolvedData resolvedData;
            if (hasExistingGroup) {
                resolvedData = questionGroupService.resolveExistingQuestionGroup(group.getQuestionGroupUuid());
            } else {
                resolvedData = questionGroupService.createQuestionGroupAndResolve(group.getNewQuestionGroup());
            }

            resolvedGroups.add(new ResolvedExamQuestionGroupPayload(
                    resolvedData.questionGroup(),
                    resolvedData.items(),
                    group.getPickQuestionCount(),
                    group.getScorePerQuestion(),
                    group.getDisplayOrder()));
        }

        return resolvedGroups;
    }

    private void validateTfPercentages(ReqCreateExamDTO request) {
        BigDecimal pct1 = request.getTfCorrect1Pct() != null ? request.getTfCorrect1Pct() : DEFAULT_TF_CORRECT_1_PCT;
        BigDecimal pct2 = request.getTfCorrect2Pct() != null ? request.getTfCorrect2Pct() : DEFAULT_TF_CORRECT_2_PCT;
        BigDecimal pct3 = request.getTfCorrect3Pct() != null ? request.getTfCorrect3Pct() : DEFAULT_TF_CORRECT_3_PCT;
        BigDecimal pct4 = request.getTfCorrect4Pct() != null ? request.getTfCorrect4Pct() : DEFAULT_TF_CORRECT_4_PCT;

        if (pct1.compareTo(pct2) > 0 || pct2.compareTo(pct3) > 0 || pct3.compareTo(pct4) > 0) {
            throw new IdInvalidException("TF scoring percentages must be non-decreasing from 1 to 4 correct statements");
        }
    }

    private void replaceExamDetails(UUID examUuid, ReqCreateExamDTO request, ValidationContext validationContext) {
        List<UUID> groupIds = examQuestionGroupRepository.findByExamUuidOrderByDisplayOrderAsc(examUuid)
                .stream()
                .map(ExamQuestionGroup::getEqgUuid)
                .toList();
        groupIds.forEach(examQuestionGroupItemRepository::deleteByEqgUuid);
        examQuestionGroupRepository.deleteByExamUuid(examUuid);
        examQuestionRepository.deleteByExamUuid(examUuid);

        if (request.getExamQuestions() != null && !request.getExamQuestions().isEmpty()) {
            List<ExamQuestion> examQuestions = request.getExamQuestions().stream()
                    .sorted(Comparator.comparing(ReqExamQuestionDTO::getQuestionOrder))
                    .map(item -> {
                        ExamQuestion examQuestion = new ExamQuestion();
                        examQuestion.setExamQuestionUuid(UuidV7Generator.generate());
                        examQuestion.setExamUuid(examUuid);
                        examQuestion.setQuestionUuid(item.getQuestionUuid());
                        examQuestion.setQuestionOrder(item.getQuestionOrder());
                        examQuestion.setScore(item.getScore());
                        examQuestion.setSectionType(item.getSectionType());
                        examQuestion.setSourceType(item.getSourceType());
                        return examQuestion;
                    })
                    .toList();
            examQuestionRepository.saveAll(examQuestions);
        }

        for (ResolvedExamQuestionGroupPayload groupRequest : validationContext.resolvedGroups().stream()
                .sorted(Comparator.comparing(ResolvedExamQuestionGroupPayload::displayOrder))
                .toList()) {
            ExamQuestionGroup group = new ExamQuestionGroup();
            group.setEqgUuid(UuidV7Generator.generate());
            group.setExamUuid(examUuid);
            group.setQuestionGroupUuid(groupRequest.questionGroup().getQuestionGroupUuid());
            group.setGroupName(groupRequest.questionGroup().getGroupName().trim());
            group.setQuestionType(groupRequest.questionGroup().getQuestionType());
            group.setQuestionTopic(groupRequest.questionGroup().getQuestionTopic());
            group.setQuestionCount(groupRequest.questionGroup().getQuestionCount());
            group.setPickQuestionCount(groupRequest.pickQuestionCount());
            group.setScorePerQuestion(groupRequest.scorePerQuestion());
            group.setDisplayOrder(groupRequest.displayOrder());
            ExamQuestionGroup savedGroup = examQuestionGroupRepository.save(group);

            List<ExamQuestionGroupItem> items = groupRequest.items().stream()
                    .map(item -> {
                        ExamQuestionGroupItem groupItem = new ExamQuestionGroupItem();
                        groupItem.setEqgiUuid(UuidV7Generator.generate());
                        groupItem.setEqgUuid(savedGroup.getEqgUuid());
                        groupItem.setQuestionUuid(item.getQuestionUuid());
                        return groupItem;
                    })
                    .toList();
            examQuestionGroupItemRepository.saveAll(items);
        }
    }

    private ExamStatus parseExamStatus(String status) {
        try {
            return ExamStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IdInvalidException("Invalid exam status: " + status);
        }
    }

    private com.DoAn1.examservice.domain.enums.ExamType parseExamType(String type) {
        try {
            return com.DoAn1.examservice.domain.enums.ExamType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IdInvalidException("Invalid exam type: " + type);
        }
    }

    private UUID resolveCurrentUserUuid() {
        return SecurityUtil.getCurrentUserUuid()
                .map(UUID::fromString)
                .orElseThrow(() -> new IdInvalidException("User id is missing from JWT"));
    }

    private ResExamDTO buildResponse(Exam exam) {
        List<ExamQuestion> storedExamQuestions = examQuestionRepository.findByExamUuidOrderByQuestionOrderAsc(exam.getExamUuid());
        List<ExamQuestionGroup> storedExamQuestionGroups = examQuestionGroupRepository.findByExamUuidOrderByDisplayOrderAsc(exam.getExamUuid());

        Map<UUID, List<ExamQuestionGroupItem>> groupItemsByGroupId = new LinkedHashMap<>();
        Set<UUID> referencedQuestionIds = new java.util.LinkedHashSet<>();

        storedExamQuestions.forEach(item -> referencedQuestionIds.add(item.getQuestionUuid()));
        for (ExamQuestionGroup group : storedExamQuestionGroups) {
            List<ExamQuestionGroupItem> items = examQuestionGroupItemRepository.findByEqgUuid(group.getEqgUuid());
            groupItemsByGroupId.put(group.getEqgUuid(), items);
            items.forEach(item -> referencedQuestionIds.add(item.getQuestionUuid()));
        }

        Map<UUID, Question> questionById = referencedQuestionIds.isEmpty()
                ? Map.of()
                : questionRepository.findAllById(referencedQuestionIds).stream()
                        .collect(Collectors.toMap(Question::getQuestionUuid, Function.identity()));

        Map<QuestionType, List<ResExamStandaloneQuestionDTO>> standaloneByType = new LinkedHashMap<>();
        Map<QuestionType, List<ResExamQuestionGroupDTO>> groupsByType = new LinkedHashMap<>();
        for (QuestionType questionType : QuestionType.values()) {
            standaloneByType.put(questionType, new ArrayList<>());
            groupsByType.put(questionType, new ArrayList<>());
        }

        for (ExamQuestion item : storedExamQuestions) {
            Question question = questionById.get(item.getQuestionUuid());
            standaloneByType.get(item.getSectionType()).add(ResExamStandaloneQuestionDTO.builder()
                    .examQuestionUuid(item.getExamQuestionUuid())
                    .questionUuid(item.getQuestionUuid())
                    .questionOrder(item.getQuestionOrder())
                    .score(item.getScore())
                    .sectionType(item.getSectionType())
                    .sourceType(item.getSourceType())
                    .questionDetail(buildQuestionDetail(question))
                    .build());
        }

        for (ExamQuestionGroup group : storedExamQuestionGroups) {
            List<ResExamQuestionGroupItemDTO> items = groupItemsByGroupId.getOrDefault(group.getEqgUuid(), List.of())
                    .stream()
                    .map(item -> ResExamQuestionGroupItemDTO.builder()
                            .eqgiUuid(item.getEqgiUuid())
                            .questionUuid(item.getQuestionUuid())
                            .questionDetail(buildQuestionDetail(questionById.get(item.getQuestionUuid())))
                            .build())
                    .toList();

            groupsByType.get(group.getQuestionType()).add(ResExamQuestionGroupDTO.builder()
                    .eqgUuid(group.getEqgUuid())
                    .questionGroupUuid(group.getQuestionGroupUuid())
                    .groupName(group.getGroupName())
                    .questionType(group.getQuestionType())
                    .questionTopic(group.getQuestionTopic())
                    .poolQuestionCount(group.getQuestionCount())
                    .pickQuestionCount(group.getPickQuestionCount())
                    .scorePerQuestion(group.getScorePerQuestion())
                    .displayOrder(group.getDisplayOrder())
                    .items(items)
                    .build());
        }

        List<ResExamQuestionTypeSectionDTO> questionSections = List.of(
                buildQuestionTypeSection(QuestionType.MCQ, standaloneByType, groupsByType),
                buildQuestionTypeSection(QuestionType.TFQ, standaloneByType, groupsByType),
                buildQuestionTypeSection(QuestionType.SAQ, standaloneByType, groupsByType));

        ResExamQuestionSummaryDTO questionSummary = ResExamQuestionSummaryDTO.builder()
                .mcqCount(calculateTotalQuestionCount(QuestionType.MCQ, standaloneByType, groupsByType))
                .tfqCount(calculateTotalQuestionCount(QuestionType.TFQ, standaloneByType, groupsByType))
                .saqCount(calculateTotalQuestionCount(QuestionType.SAQ, standaloneByType, groupsByType))
                .build();

        return ResExamDTO.builder()
                .examUuid(exam.getExamUuid())
                .examName(exam.getExamName())
                .gradeId(exam.getGradeId())
                .examType(exam.getExamType())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .durationMinutes(exam.getDurationMinutes())
                .totalScore(exam.getTotalScore())
                .numberOfAttempt(exam.getNumberOfAttempt())
                .status(exam.getStatus())
                .createdByUserUuid(exam.getCreatedByUserUuid())
                .tfCorrect1Pct(exam.getTfCorrect1Pct())
                .tfCorrect2Pct(exam.getTfCorrect2Pct())
                .tfCorrect3Pct(exam.getTfCorrect3Pct())
                .tfCorrect4Pct(exam.getTfCorrect4Pct())
                .questionSummary(questionSummary)
                .questionSections(questionSections)
                .createdAt(exam.getCreatedAt())
                .updatedAt(exam.getUpdatedAt())
                .createdBy(exam.getCreatedBy())
                .updatedBy(exam.getUpdatedBy())
                .build();
    }

    private ResExamQuestionDetailDTO buildQuestionDetail(Question question) {
        if (question == null) {
            return null;
        }
        return ResExamQuestionDetailDTO.builder()
                .questionUuid(question.getQuestionUuid())
                .questionContent(question.getQuestionContent())
                .questionTopic(question.getQuestionTopic())
                .questionType(question.getQuestionType())
                .build();
    }

    private ResExamQuestionTypeSectionDTO buildQuestionTypeSection(
            QuestionType questionType,
            Map<QuestionType, List<ResExamStandaloneQuestionDTO>> standaloneByType,
            Map<QuestionType, List<ResExamQuestionGroupDTO>> groupsByType) {
        return ResExamQuestionTypeSectionDTO.builder()
                .questionType(questionType)
                .totalQuestionCount(calculateTotalQuestionCount(questionType, standaloneByType, groupsByType))
                .standaloneQuestions(standaloneByType.getOrDefault(questionType, List.of()))
                .groups(groupsByType.getOrDefault(questionType, List.of()))
                .build();
    }

    private int calculateTotalQuestionCount(
            QuestionType questionType,
            Map<QuestionType, List<ResExamStandaloneQuestionDTO>> standaloneByType,
            Map<QuestionType, List<ResExamQuestionGroupDTO>> groupsByType) {
        int standaloneCount = standaloneByType.getOrDefault(questionType, List.of()).size();
        int groupedCount = groupsByType.getOrDefault(questionType, List.of()).stream()
                .map(ResExamQuestionGroupDTO::getPickQuestionCount)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return standaloneCount + groupedCount;
    }

    private String normalizeTopic(String topic) {
        return StringUtils.hasText(topic) ? topic.trim() : null;
    }

    private record ValidationContext(
            Map<UUID, Question> questionById,
            List<ResolvedExamQuestionGroupPayload> resolvedGroups) {
    }

    private record ResolvedExamQuestionGroupPayload(
            QuestionGroup questionGroup,
            List<QuestionGroupItem> items,
            Integer pickQuestionCount,
            BigDecimal scorePerQuestion,
            Integer displayOrder) {
    }
}
