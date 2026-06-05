package com.DoAn1.examservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.DoAn1.examservice.domain.entity.Exam;
import com.DoAn1.examservice.domain.entity.ExamAttempt;
import com.DoAn1.examservice.domain.entity.Question;
import com.DoAn1.examservice.domain.entity.QuestionAnswerKey;
import com.DoAn1.examservice.domain.entity.StudentAnswer;
import com.DoAn1.examservice.domain.enums.AttemptStatus;
import com.DoAn1.examservice.domain.enums.QuestionType;
import com.DoAn1.examservice.domain.enums.SubmitSource;
import com.DoAn1.examservice.domain.responseDTO.attempt.ResExamAttemptDTO;
import com.DoAn1.examservice.repository.ExamAttemptRepository;
import com.DoAn1.examservice.repository.ExamProctoringEventRepository;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class ExamAttemptServiceTest {

    private final ExamRepository examRepository = mock(ExamRepository.class);
    private final ExamQuestionRepository examQuestionRepository = mock(ExamQuestionRepository.class);
    private final ExamQuestionGroupRepository examQuestionGroupRepository = mock(ExamQuestionGroupRepository.class);
    private final ExamQuestionGroupItemRepository examQuestionGroupItemRepository = mock(ExamQuestionGroupItemRepository.class);
    private final ExamAttemptRepository examAttemptRepository = mock(ExamAttemptRepository.class);
    private final ExamProctoringEventRepository examProctoringEventRepository = mock(ExamProctoringEventRepository.class);
    private final StudentAnswerRepository studentAnswerRepository = mock(StudentAnswerRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final QuestionMcOptionRepository questionMcOptionRepository = mock(QuestionMcOptionRepository.class);
    private final QuestionTrueFalseStatementRepository questionTrueFalseStatementRepository = mock(QuestionTrueFalseStatementRepository.class);
    private final QuestionAnswerKeyRepository questionAnswerKeyRepository = mock(QuestionAnswerKeyRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ExamAttemptService examAttemptService;
    private UUID studentUuid;
    private UUID attemptUuid;
    private UUID examUuid;
    private UUID questionUuid;
    private ExamAttempt attempt;
    private Exam exam;

    @BeforeEach
    void setUp() {
        examAttemptService = new ExamAttemptService(
                examRepository,
                examQuestionRepository,
                examQuestionGroupRepository,
                examQuestionGroupItemRepository,
                examAttemptRepository,
                examProctoringEventRepository,
                studentAnswerRepository,
                questionRepository,
                questionMcOptionRepository,
                questionTrueFalseStatementRepository,
                questionAnswerKeyRepository,
                objectMapper);

        studentUuid = UUID.randomUUID();
        attemptUuid = UUID.randomUUID();
        examUuid = UUID.randomUUID();
        questionUuid = UUID.randomUUID();

        attempt = new ExamAttempt();
        attempt.setAttemptUuid(attemptUuid);
        attempt.setExamUuid(examUuid);
        attempt.setStudentUuid(studentUuid);
        attempt.setAttemptNo(1);
        attempt.setStartedAt(Instant.now().minusSeconds(600));
        attempt.setSubmittedAt(Instant.now().minusSeconds(300));
        attempt.setSubmitSource(SubmitSource.WEB);
        attempt.setIsAutoSubmitted(false);
        attempt.setViolationCount(0);
        attempt.setScore(new BigDecimal("2.00"));
        attempt.setQuestionSnapshotJson("""
                [{
                  "questionOrder": 1,
                  "questionUuid": "%s",
                  "questionType": "MCQ",
                  "score": 2.00,
                  "fromQuestionGroup": false,
                  "groupUuid": null,
                  "groupName": null
                }]
                """.formatted(questionUuid));

        exam = new Exam();
        exam.setExamUuid(examUuid);
        exam.setExamName("Test exam");

        Question question = new Question();
        question.setQuestionUuid(questionUuid);
        question.setQuestionContent("Question content");
        question.setQuestionType(QuestionType.MCQ);

        StudentAnswer answer = new StudentAnswer();
        answer.setQuestionUuid(questionUuid);
        answer.setRawAnswer("A");
        answer.setNormalizedAnswer("A");
        answer.setQuestionAttemptNumber(1);

        when(examAttemptRepository.findByAttemptUuid(attemptUuid)).thenReturn(Optional.of(attempt));
        when(examRepository.findByExamUuid(examUuid)).thenReturn(Optional.of(exam));
        when(questionRepository.findAllById(any())).thenReturn(List.of(question));
        when(questionMcOptionRepository.findByQuestionUuidInOrderByQuestionUuidAscOptionKeyAsc(any())).thenReturn(List.of());
        when(questionTrueFalseStatementRepository.findByQuestionUuidInOrderByQuestionUuidAscStatementOrderAsc(any()))
                .thenReturn(List.of());
        when(studentAnswerRepository.findByAttemptUuidOrderByQuestionUuidAscQuestionAttemptNumberAsc(attemptUuid))
                .thenReturn(List.of(answer));
        when(examAttemptRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void submittedAttemptReturnsWorkWithoutScore() {
        attempt.setStatus(AttemptStatus.SUBMITTED);
        exam.setEndTime(Instant.now().plusSeconds(600));

        ResExamAttemptDTO response = getAttemptAsCurrentStudent();

        assertThat(response.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(response.getScore()).isNull();
        assertThat(response.getQuestions().get(0).getCurrentRawAnswer()).isEqualTo("A");
        assertThat(response.getQuestions().get(0).getCorrectAnswerRaw()).isNull();
        assertThat(response.getQuestions().get(0).getEarnedScore()).isNull();
        verify(examAttemptRepository, never()).save(any(ExamAttempt.class));
    }

    @Test
    void scoredAttemptReturnsTotalScoreWithoutReleasedAnswers() throws Exception {
        attempt.setStatus(AttemptStatus.SCORED);
        exam.setEndTime(Instant.now().plusSeconds(600));

        ResExamAttemptDTO response = getAttemptAsCurrentStudent();

        assertThat(response.getStatus()).isEqualTo(AttemptStatus.SCORED);
        assertThat(response.getScore()).isEqualByComparingTo("2.00");
        assertThat(response.getQuestions().get(0).getCorrectAnswerRaw()).isNull();
        assertThat(response.getQuestions().get(0).getEarnedScore()).isNull();
        assertThat(objectMapper.writeValueAsString(response)).doesNotContain("correctAnswerRaw", "earnedScore");
        verify(questionAnswerKeyRepository, never()).findByQuestionUuidIn(any());
    }

    @Test
    void scoredAttemptAfterExamEndReleasesAnswersAndQuestionScore() throws Exception {
        attempt.setStatus(AttemptStatus.SCORED);
        exam.setEndTime(Instant.now().minusSeconds(1));

        QuestionAnswerKey answerKey = new QuestionAnswerKey();
        answerKey.setQuestionUuid(questionUuid);
        answerKey.setCorrectAnswerRaw("A");
        answerKey.setNormalizedAnswer("A");
        when(questionAnswerKeyRepository.findByQuestionUuidIn(any())).thenReturn(List.of(answerKey));

        ResExamAttemptDTO response = getAttemptAsCurrentStudent();

        assertThat(response.getStatus()).isEqualTo(AttemptStatus.ANSWER_RELEASED);
        assertThat(response.getQuestions().get(0).getCorrectAnswerRaw()).isEqualTo("A");
        assertThat(response.getQuestions().get(0).getCorrectNormalizedAnswer()).isEqualTo("A");
        assertThat(response.getQuestions().get(0).getEarnedScore()).isEqualByComparingTo("2.00");
        assertThat(objectMapper.writeValueAsString(response)).contains("correctAnswerRaw", "earnedScore");
        verify(examAttemptRepository).save(attempt);
    }

    @Test
    void nonStudentRoleCanViewAttemptOwnedByAnotherStudent() {
        attempt.setStatus(AttemptStatus.SUBMITTED);
        exam.setEndTime(Instant.now().plusSeconds(600));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentRoleName).thenReturn(Optional.of("TEACHER"));

            ResExamAttemptDTO response = examAttemptService.getAttempt(attemptUuid);

            assertThat(response.getAttemptUuid()).isEqualTo(attemptUuid);
            assertThat(response.getStudentUuid()).isEqualTo(studentUuid);
        }
    }

    private ResExamAttemptDTO getAttemptAsCurrentStudent() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserUuid).thenReturn(Optional.of(studentUuid.toString()));
            securityUtil.when(SecurityUtil::getCurrentRoleName).thenReturn(Optional.of("STUDENT"));
            return examAttemptService.getAttempt(attemptUuid);
        }
    }
}
