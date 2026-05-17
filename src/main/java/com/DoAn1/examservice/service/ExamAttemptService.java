package com.DoAn1.examservice.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.DoAn1.examservice.domain.entity.Exam;
import com.DoAn1.examservice.domain.entity.ExamAttempt;
import com.DoAn1.examservice.domain.entity.ExamQuestion;
import com.DoAn1.examservice.domain.entity.ExamQuestionGroup;
import com.DoAn1.examservice.domain.entity.ExamQuestionGroupItem;
import com.DoAn1.examservice.domain.entity.Question;
import com.DoAn1.examservice.domain.entity.QuestionAnswerKey;
import com.DoAn1.examservice.domain.entity.QuestionMcOption;
import com.DoAn1.examservice.domain.entity.QuestionTrueFalseStatement;
import com.DoAn1.examservice.domain.entity.StudentAnswer;
import com.DoAn1.examservice.domain.enums.AttemptStatus;
import com.DoAn1.examservice.domain.enums.ExamStatus;
import com.DoAn1.examservice.domain.enums.QuestionType;
import com.DoAn1.examservice.domain.enums.SubmitSource;
import com.DoAn1.examservice.domain.requestDTO.attempt.ReqStudentAnswerDTO;
import com.DoAn1.examservice.domain.responseDTO.attempt.ResAttemptQuestionDTO;
import com.DoAn1.examservice.domain.responseDTO.attempt.ResAttemptQuestionMcOptionDTO;
import com.DoAn1.examservice.domain.responseDTO.attempt.ResAttemptQuestionTrueFalseStatementDTO;
import com.DoAn1.examservice.domain.responseDTO.attempt.ResExamAttemptDTO;
import com.DoAn1.examservice.domain.responseDTO.attempt.ResExamAttemptSummaryDTO;
import com.DoAn1.examservice.exception.IdInvalidException;
import com.DoAn1.examservice.repository.ExamAttemptRepository;
import com.DoAn1.examservice.repository.ExamQuestionGroupItemRepository;
import com.DoAn1.examservice.repository.ExamQuestionGroupRepository;
import com.DoAn1.examservice.repository.ExamQuestionRepository;
import com.DoAn1.examservice.repository.ExamRepository;
import com.DoAn1.examservice.repository.QuestionAnswerKeyRepository;
import com.DoAn1.examservice.repository.QuestionMcOptionRepository;
import com.DoAn1.examservice.repository.QuestionRepository;
import com.DoAn1.examservice.repository.QuestionTrueFalseStatementRepository;
import com.DoAn1.examservice.repository.StudentAnswerRepository;
import com.DoAn1.examservice.util.SecurityUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExamAttemptService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamQuestionGroupRepository examQuestionGroupRepository;
    private final ExamQuestionGroupItemRepository examQuestionGroupItemRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final QuestionRepository questionRepository;
    private final QuestionMcOptionRepository questionMcOptionRepository;
    private final QuestionTrueFalseStatementRepository questionTrueFalseStatementRepository;
    private final QuestionAnswerKeyRepository questionAnswerKeyRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ResExamAttemptDTO startAttempt(UUID examUuid) {
        UUID studentUuid = resolveCurrentStudentUuid();
        Exam exam = findExamById(examUuid);
        validateExamAttemptCanStart(exam, studentUuid);

        int nextAttemptNo = examAttemptRepository.findTopByExamUuidAndStudentUuidOrderByAttemptNoDesc(examUuid, studentUuid)
                .map(existing -> existing.getAttemptNo() + 1)
                .orElse(1);

        List<AttemptQuestionSnapshot> snapshots = buildAttemptQuestionSnapshots(examUuid);

        ExamAttempt attempt = new ExamAttempt();
        attempt.setExamUuid(examUuid);
        attempt.setStudentUuid(studentUuid);
        attempt.setAttemptNo(nextAttemptNo);
        attempt.setStartedAt(Instant.now());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setIsAutoSubmitted(false);
        attempt.setSubmitSource(SubmitSource.WEB);
        attempt.setQuestionSnapshotJson(serializeSnapshots(snapshots));

        ExamAttempt savedAttempt = examAttemptRepository.save(attempt);
        return buildAttemptResponse(savedAttempt, exam);
    }

    @Transactional(readOnly = true)
    public ResExamAttemptDTO getAttempt(UUID attemptUuid) {
        ExamAttempt attempt = findAttemptById(attemptUuid);
        validateAttemptOwnership(attempt);
        Exam exam = findExamById(attempt.getExamUuid());
        attempt = autoSubmitIfExpired(attempt, exam);
        return buildAttemptResponse(attempt, exam);
    }

    @Transactional
    public Page<ResExamAttemptSummaryDTO> getAttempts(UUID examUuid, Pageable pageable) {
        UUID studentUuid = resolveCurrentStudentUuid();
        Page<ExamAttempt> attemptPage = examUuid == null
                ? examAttemptRepository.findByStudentUuid(studentUuid, pageable)
                : examAttemptRepository.findByStudentUuidAndExamUuid(studentUuid, examUuid, pageable);

        List<ExamAttempt> attempts = attemptPage.getContent().stream()
                .map(attempt -> autoSubmitIfExpired(attempt, findExamById(attempt.getExamUuid())))
                .toList();
        Map<UUID, ExamAttempt> attemptById = attempts.stream()
                .collect(Collectors.toMap(ExamAttempt::getAttemptUuid, Function.identity()));

        Map<UUID, Exam> examById = attempts.stream()
                .map(ExamAttempt::getExamUuid)
                .distinct()
                .map(this::findExamById)
                .collect(Collectors.toMap(Exam::getExamUuid, Function.identity()));

        return attemptPage.map(attempt -> buildAttemptSummaryResponse(
                attemptById.getOrDefault(attempt.getAttemptUuid(), attempt),
                examById.get(attempt.getExamUuid())));
    }

    @Transactional
    public ResExamAttemptDTO saveAnswer(UUID attemptUuid, ReqStudentAnswerDTO request) {
        ExamAttempt attempt = findAttemptById(attemptUuid);
        validateAttemptOwnership(attempt);
        Exam exam = findExamById(attempt.getExamUuid());
        attempt = autoSubmitIfExpired(attempt, exam);
        ensureAttemptInProgress(attempt);

        List<AttemptQuestionSnapshot> snapshots = deserializeSnapshots(attempt.getQuestionSnapshotJson());
        AttemptQuestionSnapshot snapshot = snapshots.stream()
                .filter(item -> item.questionUuid().equals(request.getQuestionUuid()))
                .findFirst()
                .orElseThrow(() -> new IdInvalidException("Question does not belong to this attempt: " + request.getQuestionUuid()));

        List<StudentAnswer> answerHistory = studentAnswerRepository
                .findByAttemptUuidAndQuestionUuidOrderByQuestionAttemptNumberAsc(attemptUuid, request.getQuestionUuid());

        StudentAnswer studentAnswer = new StudentAnswer();
        studentAnswer.setAttemptUuid(attemptUuid);
        studentAnswer.setQuestionUuid(request.getQuestionUuid());
        studentAnswer.setRawAnswer(request.getRawAnswer());
        studentAnswer.setNormalizedAnswer(normalizeStudentAnswer(snapshot.questionType(), request.getRawAnswer()));
        studentAnswer.setQuestionAttemptNumber(answerHistory.size() + 1);
        studentAnswer.setIsFinalAnswer(false);
        studentAnswerRepository.save(studentAnswer);

        return buildAttemptResponse(attempt, exam);
    }

    @Transactional
    public ResExamAttemptDTO submitAttempt(UUID attemptUuid) {
        ExamAttempt attempt = findAttemptById(attemptUuid);
        validateAttemptOwnership(attempt);
        ensureAttemptInProgress(attempt);

        Exam exam = findExamById(attempt.getExamUuid());
        ExamAttempt savedAttempt = finalizeAttempt(attempt, exam, false);
        return buildAttemptResponse(savedAttempt, exam);
    }

    @Transactional
    @Scheduled(fixedDelayString = "${examservice.attempt.auto-submit.fixed-delay-ms:30000}")
    public void autoSubmitExpiredAttempts() {
        List<ExamAttempt> inProgressAttempts = examAttemptRepository.findByStatusOrderByStartedAtAsc(AttemptStatus.IN_PROGRESS);
        Instant now = Instant.now();

        for (ExamAttempt attempt : inProgressAttempts) {
            Exam exam = findExamById(attempt.getExamUuid());
            if (isAttemptExpired(attempt, exam, now)) {
                finalizeAttempt(attempt, exam, true);
            }
        }
    }

    private Exam findExamById(UUID examUuid) {
        return examRepository.findById(examUuid)
                .orElseThrow(() -> new IdInvalidException("Exam not found with id: " + examUuid));
    }

    private ExamAttempt findAttemptById(UUID attemptUuid) {
        return examAttemptRepository.findById(attemptUuid)
                .orElseThrow(() -> new IdInvalidException("Attempt not found with id: " + attemptUuid));
    }

    private void validateAttemptOwnership(ExamAttempt attempt) {
        UUID currentStudentUuid = resolveCurrentStudentUuid();
        if (!attempt.getStudentUuid().equals(currentStudentUuid)) {
            throw new IdInvalidException("You do not have permission to access this attempt");
        }
    }

    private void validateExamAttemptCanStart(Exam exam, UUID studentUuid) {
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new IdInvalidException("Exam is not available for attempt");
        }

        Instant now = Instant.now();
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new IdInvalidException("Exam has not started yet");
        }
        if (exam.getEndTime() != null && now.isAfter(exam.getEndTime())) {
            throw new IdInvalidException("Exam is already closed");
        }

        int existingAttempts = examAttemptRepository.findByExamUuidAndStudentUuidOrderByAttemptNoAsc(exam.getExamUuid(), studentUuid)
                .size();
        if (exam.getNumberOfAttempt() != null && exam.getNumberOfAttempt() > 0 && existingAttempts >= exam.getNumberOfAttempt()) {
            throw new IdInvalidException("Student has reached the maximum number of attempts for this exam");
        }
    }

    private void ensureAttemptInProgress(ExamAttempt attempt) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new IdInvalidException("Attempt is not in progress");
        }
    }

    private ExamAttempt autoSubmitIfExpired(ExamAttempt attempt, Exam exam) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return attempt;
        }
        if (!isAttemptExpired(attempt, exam, Instant.now())) {
            return attempt;
        }
        return finalizeAttempt(attempt, exam, true);
    }

    private List<AttemptQuestionSnapshot> buildAttemptQuestionSnapshots(UUID examUuid) {
        List<AttemptQuestionSnapshot> snapshots = new ArrayList<>();
        int nextOrder = 1;

        List<ExamQuestion> standaloneQuestions = examQuestionRepository.findByExamUuidOrderByQuestionOrderAsc(examUuid);
        for (ExamQuestion examQuestion : standaloneQuestions) {
            snapshots.add(new AttemptQuestionSnapshot(
                    nextOrder++,
                    examQuestion.getQuestionUuid(),
                    examQuestion.getSectionType(),
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
                snapshots.add(new AttemptQuestionSnapshot(
                        nextOrder++,
                        item.getQuestionUuid(),
                        group.getQuestionType(),
                        group.getScorePerQuestion(),
                        true,
                        group.getEqgUuid(),
                        group.getGroupName()));
            }
        }

        return snapshots;
    }

    private ResExamAttemptDTO buildAttemptResponse(ExamAttempt attempt, Exam exam) {
        List<AttemptQuestionSnapshot> snapshots = deserializeSnapshots(attempt.getQuestionSnapshotJson());
        Set<UUID> questionIds = snapshots.stream().map(AttemptQuestionSnapshot::questionUuid).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Question> questionById = questionIds.isEmpty()
                ? Map.of()
                : questionRepository.findAllById(questionIds).stream()
                        .collect(Collectors.toMap(Question::getQuestionUuid, Function.identity()));

        Map<UUID, List<QuestionMcOption>> optionsByQuestion = questionMcOptionRepository
                .findByQuestionUuidInOrderByQuestionUuidAscOptionKeyAsc(new ArrayList<>(questionIds))
                .stream()
                .collect(Collectors.groupingBy(QuestionMcOption::getQuestionUuid, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, List<QuestionTrueFalseStatement>> statementsByQuestion = questionTrueFalseStatementRepository
                .findByQuestionUuidInOrderByQuestionUuidAscStatementOrderAsc(new ArrayList<>(questionIds))
                .stream()
                .collect(Collectors.groupingBy(QuestionTrueFalseStatement::getQuestionUuid, LinkedHashMap::new, Collectors.toList()));

        Map<UUID, List<StudentAnswer>> answerHistoryByQuestion = studentAnswerRepository
                .findByAttemptUuidOrderByQuestionUuidAscQuestionAttemptNumberAsc(attempt.getAttemptUuid())
                .stream()
                .collect(Collectors.groupingBy(StudentAnswer::getQuestionUuid, LinkedHashMap::new, Collectors.toList()));

        List<ResAttemptQuestionDTO> questions = snapshots.stream()
                .sorted(Comparator.comparing(AttemptQuestionSnapshot::questionOrder))
                .map(snapshot -> buildAttemptQuestionResponse(snapshot, questionById, optionsByQuestion, statementsByQuestion,
                        answerHistoryByQuestion))
                .toList();

        return ResExamAttemptDTO.builder()
                .attemptUuid(attempt.getAttemptUuid())
                .examUuid(attempt.getExamUuid())
                .examName(exam.getExamName())
                .studentUuid(attempt.getStudentUuid())
                .attemptNo(attempt.getAttemptNo())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .status(attempt.getStatus())
                .score(attempt.getScore())
                .isAutoSubmitted(attempt.getIsAutoSubmitted())
                .questions(questions)
                .build();
    }

    private ResExamAttemptSummaryDTO buildAttemptSummaryResponse(ExamAttempt attempt, Exam exam) {
        return ResExamAttemptSummaryDTO.builder()
                .attemptUuid(attempt.getAttemptUuid())
                .examUuid(attempt.getExamUuid())
                .examName(exam != null ? exam.getExamName() : null)
                .attemptNo(attempt.getAttemptNo())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .status(attempt.getStatus())
                .score(attempt.getScore())
                .isAutoSubmitted(attempt.getIsAutoSubmitted())
                .build();
    }

    private ResAttemptQuestionDTO buildAttemptQuestionResponse(
            AttemptQuestionSnapshot snapshot,
            Map<UUID, Question> questionById,
            Map<UUID, List<QuestionMcOption>> optionsByQuestion,
            Map<UUID, List<QuestionTrueFalseStatement>> statementsByQuestion,
            Map<UUID, List<StudentAnswer>> answerHistoryByQuestion) {
        Question question = questionById.get(snapshot.questionUuid());
        List<StudentAnswer> history = answerHistoryByQuestion.getOrDefault(snapshot.questionUuid(), List.of());
        StudentAnswer latestAnswer = history.isEmpty() ? null : history.get(history.size() - 1);

        return ResAttemptQuestionDTO.builder()
                .questionOrder(snapshot.questionOrder())
                .questionUuid(snapshot.questionUuid())
                .questionType(snapshot.questionType())
                .questionContent(question != null ? question.getQuestionContent() : null)
                .questionTopic(question != null ? question.getQuestionTopic() : null)
                .score(snapshot.score())
                .fromQuestionGroup(snapshot.fromQuestionGroup())
                .groupUuid(snapshot.groupUuid())
                .groupName(snapshot.groupName())
                .mcOptions(optionsByQuestion.getOrDefault(snapshot.questionUuid(), List.of()).stream()
                        .map(option -> ResAttemptQuestionMcOptionDTO.builder()
                                .optionUuid(option.getOptionUuid())
                                .optionKey(option.getOptionKey())
                                .optionContent(option.getOptionContent())
                                .build())
                        .toList())
                .tfStatements(statementsByQuestion.getOrDefault(snapshot.questionUuid(), List.of()).stream()
                        .map(statement -> ResAttemptQuestionTrueFalseStatementDTO.builder()
                                .statementUuid(statement.getStatementUuid())
                                .statementOrder(statement.getStatementOrder())
                                .statementContent(statement.getStatementContent())
                                .build())
                        .toList())
                .currentRawAnswer(latestAnswer != null ? latestAnswer.getRawAnswer() : null)
                .currentNormalizedAnswer(latestAnswer != null ? latestAnswer.getNormalizedAnswer() : null)
                .answerChangeCount(history.size())
                .build();
    }

    private ExamAttempt finalizeAttempt(ExamAttempt attempt, Exam exam, boolean autoSubmitted) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return attempt;
        }

        List<AttemptQuestionSnapshot> snapshots = deserializeSnapshots(attempt.getQuestionSnapshotJson());
        Map<UUID, List<StudentAnswer>> answerHistoryByQuestion = studentAnswerRepository
                .findByAttemptUuidOrderByQuestionUuidAscQuestionAttemptNumberAsc(attempt.getAttemptUuid())
                .stream()
                .collect(Collectors.groupingBy(StudentAnswer::getQuestionUuid, LinkedHashMap::new, Collectors.toList()));

        Set<UUID> questionIds = snapshots.stream()
                .map(AttemptQuestionSnapshot::questionUuid)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, QuestionAnswerKey> answerKeyByQuestion = questionAnswerKeyRepository.findByQuestionUuidIn(new ArrayList<>(questionIds))
                .stream()
                .collect(Collectors.toMap(QuestionAnswerKey::getQuestionUuid, Function.identity()));

        List<StudentAnswer> finalAnswersToPersist = new ArrayList<>();
        BigDecimal totalScore = BigDecimal.ZERO;

        for (AttemptQuestionSnapshot snapshot : snapshots) {
            List<StudentAnswer> history = answerHistoryByQuestion.getOrDefault(snapshot.questionUuid(), List.of());
            StudentAnswer latestAnswer = history.isEmpty() ? null : history.get(history.size() - 1);

            StudentAnswer finalAnswer = new StudentAnswer();
            finalAnswer.setAttemptUuid(attempt.getAttemptUuid());
            finalAnswer.setQuestionUuid(snapshot.questionUuid());
            finalAnswer.setRawAnswer(latestAnswer != null ? latestAnswer.getRawAnswer() : null);
            finalAnswer.setNormalizedAnswer(latestAnswer != null ? latestAnswer.getNormalizedAnswer() : null);
            finalAnswer.setQuestionAttemptNumber(history.size() + 1);
            finalAnswer.setIsFinalAnswer(true);
            finalAnswersToPersist.add(finalAnswer);

            QuestionAnswerKey answerKey = answerKeyByQuestion.get(snapshot.questionUuid());
            totalScore = totalScore.add(calculateQuestionScore(exam, attempt.getSubmitSource(), snapshot, latestAnswer, answerKey));
        }

        studentAnswerRepository.saveAll(finalAnswersToPersist);

        Instant submittedAt = Instant.now();
        attempt.setSubmittedAt(submittedAt);
        attempt.setTimeSpentSeconds((int) Duration.between(attempt.getStartedAt(), submittedAt).getSeconds());
        attempt.setStatus(AttemptStatus.SCORED);
        attempt.setScore(totalScore);
        attempt.setIsAutoSubmitted(autoSubmitted);
        return examAttemptRepository.save(attempt);
    }

    private boolean isAttemptExpired(ExamAttempt attempt, Exam exam, Instant now) {
        Instant deadline = calculateAttemptDeadline(attempt, exam);
        return deadline != null && !now.isBefore(deadline);
    }

    private Instant calculateAttemptDeadline(ExamAttempt attempt, Exam exam) {
        if (attempt.getStartedAt() == null || exam.getDurationMinutes() == null) {
            return exam.getEndTime();
        }

        Instant durationDeadline = attempt.getStartedAt().plusSeconds(exam.getDurationMinutes().longValue() * 60L);
        if (exam.getEndTime() == null) {
            return durationDeadline;
        }

        return durationDeadline.isBefore(exam.getEndTime()) ? durationDeadline : exam.getEndTime();
    }

    private BigDecimal calculateQuestionScore(
            Exam exam,
            SubmitSource submitSource,
            AttemptQuestionSnapshot snapshot,
            StudentAnswer latestAnswer,
            QuestionAnswerKey answerKey) {
        if (latestAnswer == null || answerKey == null || !StringUtils.hasText(latestAnswer.getNormalizedAnswer())) {
            return BigDecimal.ZERO;
        }

        return switch (snapshot.questionType()) {
            case MCQ -> scoreMcqQuestion(snapshot.score(), submitSource, latestAnswer.getNormalizedAnswer(), answerKey.getNormalizedAnswer());
            case TFQ -> scoreTrueFalseQuestion(exam, snapshot.score(), latestAnswer.getNormalizedAnswer(), answerKey.getNormalizedAnswer());
            case SAQ -> scoreShortAnswerQuestion(snapshot.score(), latestAnswer.getNormalizedAnswer(), answerKey.getNormalizedAnswer());
        };
    }

    private BigDecimal scoreMcqQuestion(
            BigDecimal questionScore,
            SubmitSource submitSource,
            String studentAnswer,
            String answerKey) {
        if (!StringUtils.hasText(studentAnswer) || !StringUtils.hasText(answerKey)) {
            return BigDecimal.ZERO;
        }

        if ("M".equals(studentAnswer)) {
            return BigDecimal.ZERO;
        }

        if (studentAnswer.length() != 1) {
            return BigDecimal.ZERO;
        }

        return switch (submitSource) {
            case WEB -> answerKey.contains(studentAnswer) ? questionScore : BigDecimal.ZERO;
            case OMR_IMPORT -> answerKey.equals(studentAnswer) ? questionScore : BigDecimal.ZERO;
        };
    }

    private BigDecimal scoreTrueFalseQuestion(Exam exam, BigDecimal questionScore, String studentAnswer, String answerKey) {
        if (!StringUtils.hasText(studentAnswer) || !StringUtils.hasText(answerKey) || studentAnswer.length() != answerKey.length()) {
            return BigDecimal.ZERO;
        }

        int correctCount = 0;
        for (int i = 0; i < answerKey.length(); i++) {
            char answerKeyChar = answerKey.charAt(i);
            char studentAnswerChar = studentAnswer.charAt(i);

            if (answerKeyChar == 'N') {
                if (studentAnswerChar != 'B') {
                    correctCount++;
                }
                continue;
            }

            if (studentAnswerChar == answerKeyChar) {
                correctCount++;
            }
        }

        BigDecimal percentage = switch (correctCount) {
            case 1 -> exam.getTfCorrect1Pct();
            case 2 -> exam.getTfCorrect2Pct();
            case 3 -> exam.getTfCorrect3Pct();
            case 4 -> exam.getTfCorrect4Pct();
            default -> BigDecimal.ZERO;
        };

        return questionScore.multiply(percentage).divide(new BigDecimal("100"));
    }

    private BigDecimal scoreShortAnswerQuestion(BigDecimal questionScore, String studentAnswer, String normalizedAnswerKey) {
        if (!StringUtils.hasText(studentAnswer) || !StringUtils.hasText(normalizedAnswerKey)) {
            return BigDecimal.ZERO;
        }

        List<String> acceptedAnswers = java.util.Arrays.stream(normalizedAnswerKey.split(";"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();

        return acceptedAnswers.contains(studentAnswer) ? questionScore : BigDecimal.ZERO;
    }

    private String normalizeStudentAnswer(QuestionType questionType, String rawAnswer) {
        if (rawAnswer == null) {
            return null;
        }

        String sanitized = rawAnswer.trim().toUpperCase();
        if (!StringUtils.hasText(sanitized)) {
            return null;
        }

        return switch (questionType) {
            case MCQ -> normalizeMcqAnswer(sanitized);
            case TFQ -> normalizeTfqAnswer(sanitized);
            case SAQ -> normalizeSaqStudentAnswer(rawAnswer);
        };
    }

    private String normalizeMcqAnswer(String rawAnswer) {
        String compact = rawAnswer.replaceAll("[^A-D]", "");
        if (!StringUtils.hasText(compact)) {
            return null;
        }
        return compact.chars()
                .mapToObj(value -> String.valueOf((char) value))
                .distinct()
                .sorted()
                .collect(Collectors.joining());
    }

    private String normalizeTfqAnswer(String rawAnswer) {
        String compact = rawAnswer.replaceAll("[^DSB]", "");
        return compact.length() == 4 ? compact : null;
    }

    private String normalizeSaqStudentAnswer(String rawAnswer) {
        if (rawAnswer == null) {
            return null;
        }

        if (rawAnswer.contains("|")) {
            return normalizeSaqOmrRawAnswer(rawAnswer);
        }

        String sanitized = rawAnswer.replace(' ', '_');
        if (!StringUtils.hasText(sanitized.replace("_", ""))) {
            return null;
        }
        if (sanitized.contains(";")) {
            return null;
        }
        if (sanitized.length() < 1 || sanitized.length() > 4) {
            return null;
        }

        int minusCount = 0;
        int commaCount = 0;
        for (int i = 0; i < sanitized.length(); i++) {
            char currentChar = sanitized.charAt(i);
            if (currentChar == '_') {
                continue;
            }
            if (Character.isDigit(currentChar)) {
                continue;
            }
            if (currentChar == '-') {
                minusCount++;
                if (minusCount > 1 || i != 0) {
                    return null;
                }
                continue;
            }
            if (currentChar == ',') {
                commaCount++;
                if (commaCount > 1 || (i != 1 && i != 2)) {
                    return null;
                }
                continue;
            }
            return null;
        }

        return padRightWithUnderscore(sanitized);
    }

    private String normalizeSaqOmrRawAnswer(String rawAnswer) {
        String[] columns = rawAnswer.split("\\|", -1);
        if (columns.length != 4) {
            return null;
        }

        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < columns.length; index++) {
            String columnValue = columns[index];
            if (columnValue.isEmpty()) {
                normalized.append('_');
                continue;
            }
            if (columnValue.length() > 1) {
                normalized.append('M');
                continue;
            }

            char markedChar = columnValue.charAt(0);
            if (Character.isDigit(markedChar)) {
                normalized.append(markedChar);
                continue;
            }
            if (markedChar == '-') {
                normalized.append(index == 0 ? markedChar : 'M');
                continue;
            }
            if (markedChar == ',') {
                normalized.append(index == 1 || index == 2 ? markedChar : 'M');
                continue;
            }

            return null;
        }

        return normalized.toString();
    }

    private String padRightWithUnderscore(String value) {
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < 4) {
            builder.append('_');
        }
        return builder.toString();
    }

    private String serializeSnapshots(List<AttemptQuestionSnapshot> snapshots) {
        try {
            return objectMapper.writeValueAsString(snapshots);
        } catch (JsonProcessingException ex) {
            throw new IdInvalidException("Failed to serialize attempt question snapshot", ex);
        }
    }

    private List<AttemptQuestionSnapshot> deserializeSnapshots(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, new TypeReference<List<AttemptQuestionSnapshot>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IdInvalidException("Failed to read attempt question snapshot", ex);
        }
    }

    private UUID resolveCurrentStudentUuid() {
        return SecurityUtil.getCurrentUserUuid()
                .map(UUID::fromString)
                .orElseThrow(() -> new IdInvalidException("User id is missing from JWT"));
    }

    private record AttemptQuestionSnapshot(
            Integer questionOrder,
            UUID questionUuid,
            QuestionType questionType,
            BigDecimal score,
            Boolean fromQuestionGroup,
            UUID groupUuid,
            String groupName) {
    }
}
