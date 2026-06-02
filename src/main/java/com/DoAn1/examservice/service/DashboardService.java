package com.DoAn1.examservice.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.DoAn1.examservice.domain.entity.Exam;
import com.DoAn1.examservice.domain.entity.ExamAttempt;
import com.DoAn1.examservice.domain.entity.OmrImport;
import com.DoAn1.examservice.domain.entity.Question;
import com.DoAn1.examservice.domain.entity.QuestionAnswerKey;
import com.DoAn1.examservice.domain.entity.QuestionMcOption;
import com.DoAn1.examservice.domain.entity.QuestionTrueFalseStatement;
import com.DoAn1.examservice.domain.entity.StudentAnswer;
import com.DoAn1.examservice.domain.enums.QuestionType;
import com.DoAn1.examservice.domain.enums.SubmitSource;
import com.DoAn1.examservice.domain.responseDTO.dashboard.ResExamRankingDashboardDTO;
import com.DoAn1.examservice.domain.responseDTO.dashboard.ResExamResultDashboardDTO;
import com.DoAn1.examservice.domain.responseDTO.dashboard.ResExamStatDashboardDTO;
import com.DoAn1.examservice.domain.responseDTO.dashboard.ResQuestionStatDTO;
import com.DoAn1.examservice.domain.responseDTO.dashboard.ResRankingGroupDTO;
import com.DoAn1.examservice.domain.responseDTO.dashboard.ResSectionStatDTO;
import com.DoAn1.examservice.domain.responseDTO.dashboard.ResStudentExamResultDTO;
import com.DoAn1.examservice.domain.responseDTO.dashboard.ResStudentRankingDTO;
import com.DoAn1.examservice.exception.IdInvalidException;
import com.DoAn1.examservice.repository.ExamAttemptRepository;
import com.DoAn1.examservice.repository.ExamRepository;
import com.DoAn1.examservice.repository.OmrImportRepository;
import com.DoAn1.examservice.repository.QuestionAnswerKeyRepository;
import com.DoAn1.examservice.repository.QuestionMcOptionRepository;
import com.DoAn1.examservice.repository.QuestionRepository;
import com.DoAn1.examservice.repository.QuestionTrueFalseStatementRepository;
import com.DoAn1.examservice.repository.StudentAnswerRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Bangkok");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(VIETNAM_ZONE);

    private final ExamRepository examRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final OmrImportRepository omrImportRepository;
    private final QuestionRepository questionRepository;
    private final QuestionMcOptionRepository questionMcOptionRepository;
    private final QuestionTrueFalseStatementRepository questionTrueFalseStatementRepository;
    private final QuestionAnswerKeyRepository questionAnswerKeyRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ResExamResultDashboardDTO getExamResults(UUID examUuid) {
        DashboardData data = loadDashboardData(examUuid);
        return ResExamResultDashboardDTO.builder()
                .examUuid(data.exam().getExamUuid())
                .schoolYear(data.exam().getSchoolYear())
                .examName(data.exam().getExamName())
                .startTime(data.exam().getStartTime())
                .endTime(data.exam().getEndTime())
                .createdBy(data.exam().getCreatedBy())
                .students(data.studentResults())
                .build();
    }

    @Transactional(readOnly = true)
    public ResExamStatDashboardDTO getExamStats(UUID examUuid) {
        DashboardData data = loadDashboardData(examUuid);
        List<ResSectionStatDTO> sections = new ArrayList<>();
        for (QuestionType questionType : QuestionType.values()) {
            List<BigDecimal> scores = data.studentResults().stream()
                    .map(result -> result.getSectionScores().getOrDefault(questionType, BigDecimal.ZERO))
                    .toList();
            sections.add(ResSectionStatDTO.builder()
                    .sectionType(questionType)
                    .averageScore(average(scores))
                    .meanScore(average(scores))
                    .standardDeviationScore(standardDeviation(scores))
                    .questions(buildQuestionStats(data, questionType))
                    .build());
        }
        return ResExamStatDashboardDTO.builder()
                .examUuid(data.exam().getExamUuid())
                .schoolYear(data.exam().getSchoolYear())
                .examName(data.exam().getExamName())
                .startTime(data.exam().getStartTime())
                .endTime(data.exam().getEndTime())
                .createdBy(data.exam().getCreatedBy())
                .sections(sections)
                .build();
    }

    @Transactional(readOnly = true)
    public ResExamRankingDashboardDTO getExamRanking(UUID examUuid, int topN) {
        DashboardData data = loadDashboardData(examUuid);
        int normalizedTopN = Math.max(topN, 1);
        Map<String, List<AttemptScoreContext>> omrByPaperCode = data.attemptScores().stream()
                .filter(item -> item.attempt().getSubmitSource() == SubmitSource.OMR_IMPORT)
                .collect(Collectors.groupingBy(
                        item -> firstText(data.paperCodeByAttemptUuid().get(item.attempt().getAttemptUuid()), "UNKNOWN"),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<ResRankingGroupDTO> paperRankings = omrByPaperCode.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> ResRankingGroupDTO.builder()
                        .paperCode(entry.getKey())
                        .submitSource(SubmitSource.OMR_IMPORT)
                        .students(rankTopWithTies(entry.getValue(), normalizedTopN))
                        .build())
                .toList();
        ResRankingGroupDTO webRanking = ResRankingGroupDTO.builder()
                .paperCode(null)
                .submitSource(SubmitSource.WEB)
                .students(rankTopWithTies(data.attemptScores().stream()
                        .filter(item -> item.attempt().getSubmitSource() == SubmitSource.WEB)
                        .toList(), normalizedTopN))
                .build();
        return ResExamRankingDashboardDTO.builder()
                .examUuid(data.exam().getExamUuid())
                .schoolYear(data.exam().getSchoolYear())
                .examName(data.exam().getExamName())
                .startTime(data.exam().getStartTime())
                .endTime(data.exam().getEndTime())
                .createdBy(data.exam().getCreatedBy())
                .requestedTopN(normalizedTopN)
                .paperRankings(paperRankings)
                .webRanking(webRanking)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] exportExamResults(UUID examUuid) {
        DashboardData data = loadDashboardData(examUuid);
        try (Workbook workbook = new XSSFWorkbook()) {
            addCommonInfoSheet(workbook, data.exam());
            addResultSummarySheet(workbook, data);
            addAnswerDetailSheet(workbook, data);
            return toByteArray(workbook);
        } catch (IOException ex) {
            throw new IdInvalidException("Failed to export exam results", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportExamStats(UUID examUuid) {
        ResExamStatDashboardDTO stats = getExamStats(examUuid);
        Exam exam = findExamById(examUuid);
        try (Workbook workbook = new XSSFWorkbook()) {
            addCommonInfoSheet(workbook, exam);
            addSectionStatsSheet(workbook, stats);
            addQuestionStatsSheet(workbook, stats);
            return toByteArray(workbook);
        } catch (IOException ex) {
            throw new IdInvalidException("Failed to export exam stats", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportExamRanking(UUID examUuid, int topN) {
        ResExamRankingDashboardDTO ranking = getExamRanking(examUuid, topN);
        Exam exam = findExamById(examUuid);
        try (Workbook workbook = new XSSFWorkbook()) {
            addCommonInfoSheet(workbook, exam);
            for (ResRankingGroupDTO group : ranking.getPaperRankings()) {
                addRankingSheet(workbook, sanitizeSheetName("Ma de " + group.getPaperCode()), group);
            }
            addRankingSheet(workbook, "Web", ranking.getWebRanking());
            return toByteArray(workbook);
        } catch (IOException ex) {
            throw new IdInvalidException("Failed to export exam ranking", ex);
        }
    }

    private DashboardData loadDashboardData(UUID examUuid) {
        Exam exam = findExamById(examUuid);
        List<ExamAttempt> attempts = examAttemptRepository.findByExamUuid(examUuid).stream()
                .filter(attempt -> attempt.getScore() != null)
                .sorted(Comparator.comparing(ExamAttempt::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<UUID> attemptUuids = attempts.stream().map(ExamAttempt::getAttemptUuid).toList();
        Map<UUID, List<StudentAnswer>> answersByAttempt = attemptUuids.isEmpty()
                ? Map.of()
                : studentAnswerRepository.findByAttemptUuidIn(attemptUuids).stream()
                        .collect(Collectors.groupingBy(StudentAnswer::getAttemptUuid));
        Map<UUID, String> paperCodeByAttemptUuid = attemptUuids.isEmpty()
                ? Map.of()
                : omrImportRepository.findByAttemptUuidIn(attemptUuids).stream()
                        .collect(Collectors.toMap(OmrImport::getAttemptUuid, OmrImport::getPaperCode, (first, second) -> first));
        List<AttemptScoreContext> attemptScores = attempts.stream()
                .map(attempt -> buildAttemptScoreContext(exam, attempt, answersByAttempt.getOrDefault(attempt.getAttemptUuid(), List.of())))
                .toList();
        Set<UUID> questionIds = attemptScores.stream()
                .flatMap(context -> context.questionScores().stream())
                .map(QuestionScoreContext::snapshot)
                .map(AttemptQuestionSnapshot::questionUuid)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Question> questionById = questionIds.isEmpty()
                ? Map.of()
                : questionRepository.findAllById(questionIds).stream()
                        .collect(Collectors.toMap(Question::getQuestionUuid, Function.identity()));
        Map<UUID, QuestionAnswerKey> answerKeyByQuestion = questionIds.isEmpty()
                ? Map.of()
                : questionAnswerKeyRepository.findByQuestionUuidIn(new ArrayList<>(questionIds)).stream()
                        .collect(Collectors.toMap(QuestionAnswerKey::getQuestionUuid, Function.identity()));
        Map<UUID, List<QuestionMcOption>> optionsByQuestion = questionIds.isEmpty()
                ? Map.of()
                : questionMcOptionRepository.findByQuestionUuidInOrderByQuestionUuidAscOptionKeyAsc(new ArrayList<>(questionIds)).stream()
                        .collect(Collectors.groupingBy(QuestionMcOption::getQuestionUuid, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<QuestionTrueFalseStatement>> statementsByQuestion = questionIds.isEmpty()
                ? Map.of()
                : questionTrueFalseStatementRepository.findByQuestionUuidInOrderByQuestionUuidAscStatementOrderAsc(new ArrayList<>(questionIds)).stream()
                        .collect(Collectors.groupingBy(QuestionTrueFalseStatement::getQuestionUuid, LinkedHashMap::new, Collectors.toList()));
        List<ResStudentExamResultDTO> studentResults = attemptScores.stream()
                .map(context -> ResStudentExamResultDTO.builder()
                        .studentId(context.attempt().getStudentId())
                        .fullname(context.attempt().getStudentFullname())
                        .userUuid(context.attempt().getStudentUuid())
                        .submitSource(context.attempt().getSubmitSource())
                        .paperCode(paperCodeByAttemptUuid.get(context.attempt().getAttemptUuid()))
                        .totalScore(context.attempt().getScore())
                        .violationCount(nullSafeViolationCount(context.attempt()))
                        .sectionScores(context.sectionScores())
                        .build())
                .toList();
        return new DashboardData(exam, attemptScores, studentResults, paperCodeByAttemptUuid,
                questionById, answerKeyByQuestion, optionsByQuestion, statementsByQuestion);
    }

    private AttemptScoreContext buildAttemptScoreContext(Exam exam, ExamAttempt attempt, List<StudentAnswer> answers) {
        List<AttemptQuestionSnapshot> snapshots = deserializeSnapshots(attempt.getQuestionSnapshotJson());
        Map<UUID, StudentAnswer> finalAnswerByQuestion = answers.stream()
                .collect(Collectors.groupingBy(StudentAnswer::getQuestionUuid))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> selectFinalAnswer(entry.getValue())));
        Set<UUID> questionIds = snapshots.stream().map(AttemptQuestionSnapshot::questionUuid)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, QuestionAnswerKey> answerKeyByQuestion = questionIds.isEmpty()
                ? Map.of()
                : questionAnswerKeyRepository.findByQuestionUuidIn(new ArrayList<>(questionIds)).stream()
                        .collect(Collectors.toMap(QuestionAnswerKey::getQuestionUuid, Function.identity()));
        Map<QuestionType, BigDecimal> sectionScores = emptySectionScoreMap();
        List<QuestionScoreContext> questionScores = new ArrayList<>();
        for (AttemptQuestionSnapshot snapshot : snapshots) {
            StudentAnswer finalAnswer = finalAnswerByQuestion.get(snapshot.questionUuid());
            BigDecimal questionScore = calculateQuestionScore(exam, attempt.getSubmitSource(),
                    snapshot, finalAnswer, answerKeyByQuestion.get(snapshot.questionUuid()));
            sectionScores.merge(snapshot.questionType(), questionScore, BigDecimal::add);
            questionScores.add(new QuestionScoreContext(snapshot, finalAnswer, questionScore));
        }
        return new AttemptScoreContext(attempt, sectionScores, questionScores);
    }

    private StudentAnswer selectFinalAnswer(List<StudentAnswer> answers) {
        return answers.stream()
                .filter(answer -> Boolean.TRUE.equals(answer.getIsFinalAnswer()))
                .max(Comparator.comparing(StudentAnswer::getQuestionAttemptNumber))
                .orElseGet(() -> answers.stream()
                        .max(Comparator.comparing(StudentAnswer::getQuestionAttemptNumber))
                        .orElse(null));
    }

    private List<ResQuestionStatDTO> buildQuestionStats(DashboardData data, QuestionType questionType) {
        Map<UUID, AttemptQuestionSnapshot> snapshotByQuestion = new LinkedHashMap<>();
        Map<UUID, Map<String, Long>> answerCountsByQuestion = new LinkedHashMap<>();
        for (AttemptScoreContext attemptScore : data.attemptScores()) {
            for (QuestionScoreContext questionScore : attemptScore.questionScores()) {
                AttemptQuestionSnapshot snapshot = questionScore.snapshot();
                if (snapshot.questionType() != questionType) {
                    continue;
                }
                snapshotByQuestion.putIfAbsent(snapshot.questionUuid(), snapshot);
                Map<String, Long> counts = answerCountsByQuestion.computeIfAbsent(snapshot.questionUuid(), id -> initializedAnswerCounts(snapshot, data));
                counts.merge(displayStudentAnswer(snapshot, questionScore.answer()), 1L, Long::sum);
            }
        }
        return snapshotByQuestion.values().stream()
                .sorted(Comparator.comparing(AttemptQuestionSnapshot::questionOrder))
                .map(snapshot -> ResQuestionStatDTO.builder()
                        .questionOrder(snapshot.questionOrder())
                        .questionUuid(snapshot.questionUuid())
                        .questionType(snapshot.questionType())
                        .questionContent(buildQuestionDisplayContent(
                                data.questionById().get(snapshot.questionUuid()),
                                data.optionsByQuestion().getOrDefault(snapshot.questionUuid(), List.of()),
                                data.statementsByQuestion().getOrDefault(snapshot.questionUuid(), List.of())))
                        .correctAnswer(correctAnswer(data.answerKeyByQuestion().get(snapshot.questionUuid())))
                        .answerCounts(answerCountsByQuestion.getOrDefault(snapshot.questionUuid(), Map.of()))
                        .build())
                .toList();
    }

    private Map<String, Long> initializedAnswerCounts(AttemptQuestionSnapshot snapshot, DashboardData data) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (snapshot.questionType() == QuestionType.MCQ) {
            data.optionsByQuestion().getOrDefault(snapshot.questionUuid(), List.of())
                    .forEach(option -> counts.put(option.getOptionKey(), 0L));
            counts.putIfAbsent("M", 0L);
        }
        counts.put("Bỏ trống", 0L);
        return counts;
    }

    private List<ResStudentRankingDTO> rankTopWithTies(List<AttemptScoreContext> attemptScores, int topN) {
        List<AttemptScoreContext> sorted = attemptScores.stream()
                .sorted(Comparator.comparing((AttemptScoreContext item) -> nullSafeScore(item.attempt().getScore())).reversed()
                        .thenComparing(item -> nullToEmpty(item.attempt().getStudentId()))
                        .thenComparing(item -> nullToEmpty(item.attempt().getStudentFullname())))
                .toList();
        if (sorted.isEmpty()) {
            return List.of();
        }
        BigDecimal threshold = sorted.size() <= topN ? sorted.get(sorted.size() - 1).attempt().getScore() : sorted.get(topN - 1).attempt().getScore();
        List<ResStudentRankingDTO> ranking = new ArrayList<>();
        int visibleRank = 0;
        BigDecimal previousScore = null;
        for (int index = 0; index < sorted.size(); index++) {
            AttemptScoreContext context = sorted.get(index);
            BigDecimal score = context.attempt().getScore();
            if (score.compareTo(threshold) < 0) {
                break;
            }
            if (previousScore == null || score.compareTo(previousScore) != 0) {
                visibleRank = index + 1;
                previousScore = score;
            }
            ranking.add(ResStudentRankingDTO.builder()
                    .rank(visibleRank)
                    .studentId(context.attempt().getStudentId())
                    .fullname(context.attempt().getStudentFullname())
                    .userUuid(context.attempt().getStudentUuid())
                    .score(score)
                    .build());
        }
        return ranking;
    }

    private BigDecimal calculateQuestionScore(Exam exam, SubmitSource submitSource, AttemptQuestionSnapshot snapshot, StudentAnswer answer, QuestionAnswerKey answerKey) {
        if (answer == null || answerKey == null || !StringUtils.hasText(answer.getNormalizedAnswer())) {
            return BigDecimal.ZERO;
        }
        return switch (snapshot.questionType()) {
            case MCQ -> scoreMcqQuestion(snapshot.score(), submitSource, answer.getNormalizedAnswer(), answerKey.getNormalizedAnswer());
            case TFQ -> scoreTrueFalseQuestion(exam, snapshot.score(), answer.getNormalizedAnswer(), answerKey.getNormalizedAnswer());
            case SAQ -> scoreShortAnswerQuestion(snapshot.score(), answer.getNormalizedAnswer(), answerKey.getNormalizedAnswer());
        };
    }

    private BigDecimal scoreMcqQuestion(BigDecimal questionScore, SubmitSource submitSource, String studentAnswer, String answerKey) {
        if (!StringUtils.hasText(studentAnswer) || !StringUtils.hasText(answerKey) || "M".equals(studentAnswer) || studentAnswer.length() != 1) {
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
        for (int index = 0; index < answerKey.length(); index++) {
            char keyChar = answerKey.charAt(index);
            char answerChar = studentAnswer.charAt(index);
            if (keyChar == 'N') {
                if (answerChar != 'B') {
                    correctCount++;
                }
            } else if (answerChar == keyChar) {
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
        return questionScore.multiply(percentage).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreShortAnswerQuestion(BigDecimal questionScore, String studentAnswer, String answerKey) {
        if (!StringUtils.hasText(studentAnswer) || !StringUtils.hasText(answerKey)) {
            return BigDecimal.ZERO;
        }
        List<String> acceptedAnswers = java.util.Arrays.stream(answerKey.split(";"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return acceptedAnswers.contains(studentAnswer) ? questionScore : BigDecimal.ZERO;
    }

    private void addCommonInfoSheet(Workbook workbook, Exam exam) {
        Sheet sheet = workbook.createSheet("Thông tin chung");
        writeKeyValue(sheet, 0, "Năm học", exam.getSchoolYear());
        writeKeyValue(sheet, 1, "Tên bài kiểm tra", exam.getExamName());
        writeKeyValue(sheet, 2, "Ngày mở bài kiểm tra", formatInstant(exam.getStartTime()));
        writeKeyValue(sheet, 3, "Ngày đóng bài kiểm tra", formatInstant(exam.getEndTime()));
        writeKeyValue(sheet, 4, "Người tạo bài kiểm tra", exam.getCreatedBy());
        autoSize(sheet, 2);
    }

    private void addResultSummarySheet(Workbook workbook, DashboardData data) {
        Sheet sheet = workbook.createSheet("Kết quả");
        writeHeader(workbook, sheet, 0, List.of("SID", "Họ tên", "User UUID", "Nguồn", "Mã đề giấy", "Tổng điểm", "Số vi phạm", "Điểm MCQ", "Điểm TFQ", "Điểm SAQ"));
        int rowIndex = 1;
        for (ResStudentExamResultDTO student : data.studentResults()) {
            Row row = sheet.createRow(rowIndex++);
            writeCells(row, student.getStudentId(), student.getFullname(), student.getUserUuid(), student.getSubmitSource(), student.getPaperCode(),
                    student.getTotalScore(), student.getViolationCount(), student.getSectionScores().get(QuestionType.MCQ), student.getSectionScores().get(QuestionType.TFQ),
                    student.getSectionScores().get(QuestionType.SAQ));
        }
        autoSize(sheet, 10);
    }

    private void addAnswerDetailSheet(Workbook workbook, DashboardData data) {
        Sheet sheet = workbook.createSheet("Chi tiết đáp án");
        writeHeader(workbook, sheet, 0, List.of("SID", "Họ tên", "User UUID", "Nguồn", "Mã đề giấy", "Phần", "STT câu", "Nội dung câu hỏi", "Đáp án học sinh chọn", "Đáp án đúng", "Điểm câu"));
        int rowIndex = 1;
        for (AttemptScoreContext attemptScore : data.attemptScores()) {
            ExamAttempt attempt = attemptScore.attempt();
            for (QuestionScoreContext questionScore : attemptScore.questionScores()) {
                AttemptQuestionSnapshot snapshot = questionScore.snapshot();
                Row row = sheet.createRow(rowIndex++);
                writeCells(row, attempt.getStudentId(), attempt.getStudentFullname(), attempt.getStudentUuid(), attempt.getSubmitSource(),
                        attempt.getSubmitSource() == SubmitSource.WEB ? null : data.paperCodeByAttemptUuid().get(attempt.getAttemptUuid()),
                        snapshot.questionType(), snapshot.questionOrder(),
                        buildQuestionDisplayContent(data.questionById().get(snapshot.questionUuid()),
                                data.optionsByQuestion().getOrDefault(snapshot.questionUuid(), List.of()),
                                data.statementsByQuestion().getOrDefault(snapshot.questionUuid(), List.of())),
                        displayStudentAnswer(snapshot, questionScore.answer()),
                        correctAnswer(data.answerKeyByQuestion().get(snapshot.questionUuid())),
                        questionScore.score());
            }
        }
        autoSize(sheet, 11);
    }

    private void addSectionStatsSheet(Workbook workbook, ResExamStatDashboardDTO stats) {
        Sheet sheet = workbook.createSheet("Thống kê phần");
        writeHeader(workbook, sheet, 0, List.of("Phần", "Điểm trung bình", "Mean", "Độ lệch chuẩn"));
        int rowIndex = 1;
        for (ResSectionStatDTO section : stats.getSections()) {
            Row row = sheet.createRow(rowIndex++);
            writeCells(row, section.getSectionType(), section.getAverageScore(), section.getMeanScore(), section.getStandardDeviationScore());
        }
        autoSize(sheet, 4);
    }

    private void addQuestionStatsSheet(Workbook workbook, ResExamStatDashboardDTO stats) {
        Sheet sheet = workbook.createSheet("Thống kê câu hỏi");
        writeHeader(workbook, sheet, 0, List.of("Phần", "STT câu", "Nội dung câu hỏi", "Đáp án đúng", "Số lượt chọn"));
        int rowIndex = 1;
        for (ResSectionStatDTO section : stats.getSections()) {
            for (ResQuestionStatDTO question : section.getQuestions()) {
                Row row = sheet.createRow(rowIndex++);
                writeCells(row, section.getSectionType(), question.getQuestionOrder(), question.getQuestionContent(), question.getCorrectAnswer(), joinCounts(question.getAnswerCounts()));
            }
        }
        autoSize(sheet, 5);
    }

    private void addRankingSheet(Workbook workbook, String sheetName, ResRankingGroupDTO group) {
        Sheet sheet = workbook.createSheet(sheetName);
        writeHeader(workbook, sheet, 0, List.of("Hạng", "SID", "Họ tên", "User UUID", "Điểm"));
        int rowIndex = 1;
        for (ResStudentRankingDTO student : group.getStudents()) {
            Row row = sheet.createRow(rowIndex++);
            writeCells(row, student.getRank(), student.getStudentId(), student.getFullname(), student.getUserUuid(), student.getScore());
        }
        autoSize(sheet, 5);
    }

    private String buildQuestionDisplayContent(Question question, List<QuestionMcOption> options, List<QuestionTrueFalseStatement> statements) {
        if (question == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(question.getQuestionContent());
        if (question.getQuestionType() == QuestionType.MCQ) {
            for (QuestionMcOption option : options) {
                builder.append(" | ").append(option.getOptionKey()).append(". ").append(option.getOptionContent());
            }
        }
        if (question.getQuestionType() == QuestionType.TFQ) {
            for (QuestionTrueFalseStatement statement : statements) {
                builder.append(" | ").append(statement.getStatementOrder()).append(". ").append(statement.getStatementContent());
            }
        }
        return builder.toString();
    }

    private String displayStudentAnswer(AttemptQuestionSnapshot snapshot, StudentAnswer answer) {
        if (answer == null) {
            return "Bỏ trống";
        }
        if (snapshot.questionType() == QuestionType.SAQ) {
            return StringUtils.hasText(answer.getRawAnswer()) ? answer.getRawAnswer() : "Bỏ trống";
        }
        return StringUtils.hasText(answer.getNormalizedAnswer()) ? answer.getNormalizedAnswer() : "Bỏ trống";
    }

    private String correctAnswer(QuestionAnswerKey answerKey) {
        return answerKey == null ? null : answerKey.getNormalizedAnswer();
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal standardDeviation(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = average(values);
        BigDecimal variance = values.stream()
                .map(value -> value.subtract(mean).multiply(value.subtract(mean)))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue())).setScale(4, RoundingMode.HALF_UP);
    }

    private Map<QuestionType, BigDecimal> emptySectionScoreMap() {
        Map<QuestionType, BigDecimal> sectionScores = new LinkedHashMap<>();
        for (QuestionType questionType : QuestionType.values()) {
            sectionScores.put(questionType, BigDecimal.ZERO);
        }
        return sectionScores;
    }

    private List<AttemptQuestionSnapshot> deserializeSnapshots(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, new TypeReference<List<AttemptQuestionSnapshot>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IdInvalidException("Failed to read attempt question snapshot", ex);
        }
    }

    private Exam findExamById(UUID examUuid) {
        return examRepository.findByExamUuid(examUuid)
                .orElseThrow(() -> new IdInvalidException("Exam not found with id: " + examUuid));
    }

    private byte[] toByteArray(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void writeKeyValue(Sheet sheet, int rowIndex, String key, Object value) {
        Row row = sheet.createRow(rowIndex);
        writeCell(row.createCell(0), key);
        writeCell(row.createCell(1), value);
    }

    private void writeHeader(Workbook workbook, Sheet sheet, int rowIndex, List<String> headers) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < headers.size(); index++) {
            Cell cell = row.createCell(index);
            cell.setCellStyle(style);
            writeCell(cell, headers.get(index));
        }
    }

    private void writeCells(Row row, Object... values) {
        for (int index = 0; index < values.length; index++) {
            writeCell(row.createCell(index), values[index]);
        }
    }

    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private void autoSize(Sheet sheet, int columns) {
        for (int index = 0; index < columns; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private String formatInstant(Instant value) {
        return value == null ? null : DATE_TIME_FORMATTER.format(value);
    }

    private String joinCounts(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    private String sanitizeSheetName(String value) {
        String sanitized = value.replaceAll("[\\\\\\\\/?*\\[\\]:]", " ").trim();
        if (sanitized.isBlank()) {
            sanitized = "Sheet";
        }
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    private String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal nullSafeScore(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Integer nullSafeViolationCount(ExamAttempt attempt) {
        return attempt.getViolationCount() == null ? 0 : attempt.getViolationCount();
    }

    private record DashboardData(
            Exam exam,
            List<AttemptScoreContext> attemptScores,
            List<ResStudentExamResultDTO> studentResults,
            Map<UUID, String> paperCodeByAttemptUuid,
            Map<UUID, Question> questionById,
            Map<UUID, QuestionAnswerKey> answerKeyByQuestion,
            Map<UUID, List<QuestionMcOption>> optionsByQuestion,
            Map<UUID, List<QuestionTrueFalseStatement>> statementsByQuestion) {
    }

    private record AttemptScoreContext(ExamAttempt attempt, Map<QuestionType, BigDecimal> sectionScores, List<QuestionScoreContext> questionScores) {
    }

    private record QuestionScoreContext(AttemptQuestionSnapshot snapshot, StudentAnswer answer, BigDecimal score) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
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
