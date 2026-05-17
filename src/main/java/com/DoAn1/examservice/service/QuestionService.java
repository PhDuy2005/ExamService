package com.DoAn1.examservice.service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.DoAn1.examservice.domain.entity.Question;
import com.DoAn1.examservice.domain.entity.QuestionAnswerKey;
import com.DoAn1.examservice.domain.entity.QuestionMcOption;
import com.DoAn1.examservice.domain.entity.QuestionTrueFalseStatement;
import com.DoAn1.examservice.domain.enums.QuestionType;
import com.DoAn1.examservice.domain.requestDTO.question.ReqCreateQuestionDTO;
import com.DoAn1.examservice.domain.requestDTO.question.ReqQuestionActivationDTO;
import com.DoAn1.examservice.domain.requestDTO.question.ReqQuestionMcOptionDTO;
import com.DoAn1.examservice.domain.requestDTO.question.ReqQuestionTrueFalseStatementDTO;
import com.DoAn1.examservice.domain.requestDTO.question.ReqUpdateQuestionDTO;
import com.DoAn1.examservice.domain.responseDTO.question.ResQuestionDTO;
import com.DoAn1.examservice.domain.responseDTO.question.ResQuestionMcOptionDTO;
import com.DoAn1.examservice.domain.responseDTO.question.ResQuestionTrueFalseStatementDTO;
import com.DoAn1.examservice.exception.IdInvalidException;
import com.DoAn1.examservice.repository.QuestionAnswerKeyRepository;
import com.DoAn1.examservice.repository.QuestionMcOptionRepository;
import com.DoAn1.examservice.repository.QuestionRepository;
import com.DoAn1.examservice.repository.QuestionTrueFalseStatementRepository;
import com.DoAn1.examservice.util.SecurityUtil;
import com.DoAn1.examservice.util.UuidV7Generator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionMcOptionRepository questionMcOptionRepository;
    private final QuestionTrueFalseStatementRepository questionTrueFalseStatementRepository;
    private final QuestionAnswerKeyRepository questionAnswerKeyRepository;

    @Transactional
    public ResQuestionDTO createQuestion(ReqCreateQuestionDTO request) {
        validateQuestionPayload(request);

        Question question = new Question();
        applyQuestionData(question, request);
        question.setCreatedByUserUuid(resolveCurrentUserUuid());
        Question savedQuestion = questionRepository.save(question);

        replaceQuestionDetails(savedQuestion.getQuestionUuid(), request);
        return buildResponse(savedQuestion);
    }

    @Transactional(readOnly = true)
    public ResQuestionDTO getQuestion(UUID questionUuid) {
        return buildResponse(findQuestionById(questionUuid));
    }

    @Transactional(readOnly = true)
    public Page<ResQuestionDTO> getQuestions(Long gradeId, String topic, String content, String type, Boolean isActive,
            Pageable pageable) {
        Specification<Question> specification = (root, query, cb) -> cb.conjunction();

        if (gradeId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("gradeId"), gradeId));
        }
        if (StringUtils.hasText(topic)) {
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("questionTopic")),
                    "%" + topic.trim().toLowerCase() + "%"));
        }
        if (StringUtils.hasText(content)) {
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("questionContent")),
                    "%" + content.trim().toLowerCase() + "%"));
        }
        if (StringUtils.hasText(type)) {
            QuestionType questionType;
            try {
                questionType = QuestionType.valueOf(type.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IdInvalidException("Invalid question type: " + type);
            }
            specification = specification.and((root, query, cb) -> cb.equal(root.get("questionType"), questionType));
        }
        if (isActive != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("isActive"), isActive));
        }

        return questionRepository.findAll(specification, pageable)
                .map(this::buildResponse);
    }

    @Transactional
    public ResQuestionDTO updateQuestion(UUID questionUuid, ReqUpdateQuestionDTO request) {
        validateQuestionPayload(request);

        Question question = findQuestionById(questionUuid);
        UUID createdByUserUuid = question.getCreatedByUserUuid();
        applyQuestionData(question, request);
        question.setCreatedByUserUuid(createdByUserUuid);
        Question savedQuestion = questionRepository.save(question);

        replaceQuestionDetails(savedQuestion.getQuestionUuid(), request);
        return buildResponse(savedQuestion);
    }

    @Transactional
    public ResQuestionDTO updateQuestionActivation(UUID questionUuid, ReqQuestionActivationDTO request) {
        Question question = findQuestionById(questionUuid);
        question.setIsActive(request.getIsActive());
        return buildResponse(questionRepository.save(question));
    }

    private Question findQuestionById(UUID questionUuid) {
        return questionRepository.findById(questionUuid)
                .orElseThrow(() -> new IdInvalidException("Question not found with id: " + questionUuid));
    }

    private void applyQuestionData(Question question, ReqCreateQuestionDTO request) {
        question.setGradeId(request.getGradeId());
        question.setQuestionContent(request.getQuestionContent().trim());
        question.setQuestionTopic(
                StringUtils.hasText(request.getQuestionTopic()) ? request.getQuestionTopic().trim() : null);
        question.setQuestionType(request.getQuestionType());
        question.setIsActive(request.getIsActive() == null ? Boolean.TRUE : request.getIsActive());
    }

    private void replaceQuestionDetails(UUID questionUuid, ReqCreateQuestionDTO request) {
        questionMcOptionRepository.deleteByQuestionUuid(questionUuid);
        questionTrueFalseStatementRepository.deleteByQuestionUuid(questionUuid);
        questionAnswerKeyRepository.deleteByQuestionUuid(questionUuid);

        if (request.getQuestionType() == QuestionType.MCQ) {
            List<QuestionMcOption> options = request.getMcOptions().stream()
                    .sorted(Comparator.comparing(ReqQuestionMcOptionDTO::getOptionKey))
                    .map(item -> {
                        QuestionMcOption option = new QuestionMcOption();
                        option.setOptionUuid(UuidV7Generator.generate());
                        option.setQuestionUuid(questionUuid);
                        option.setOptionKey(item.getOptionKey().trim().toUpperCase());
                        option.setOptionContent(item.getOptionContent().trim());
                        return option;
                    })
                    .toList();
            questionMcOptionRepository.saveAll(options);
        }

        if (request.getQuestionType() == QuestionType.TFQ) {
            List<QuestionTrueFalseStatement> statements = request.getTfStatements().stream()
                    .sorted(Comparator.comparing(ReqQuestionTrueFalseStatementDTO::getStatementOrder))
                    .map(item -> {
                        QuestionTrueFalseStatement statement = new QuestionTrueFalseStatement();
                        statement.setStatementUuid(UuidV7Generator.generate());
                        statement.setQuestionUuid(questionUuid);
                        statement.setStatementOrder(item.getStatementOrder());
                        statement.setStatementContent(item.getStatementContent().trim());
                        return statement;
                    })
                    .toList();
            questionTrueFalseStatementRepository.saveAll(statements);
        }

        QuestionAnswerKey answerKey = new QuestionAnswerKey();
        answerKey.setAnswerKeyUuid(UuidV7Generator.generate());
        answerKey.setQuestionUuid(questionUuid);
        answerKey.setCorrectAnswerRaw(request.getAnswerKey().getCorrectAnswerRaw().trim());
        answerKey.setNormalizedAnswer(
                normalizeAnswer(request.getQuestionType(), request.getAnswerKey().getCorrectAnswerRaw()));
        questionAnswerKeyRepository.save(answerKey);
    }

    private void validateQuestionPayload(ReqCreateQuestionDTO request) {
        if (request.getQuestionType() == QuestionType.MCQ) {
            List<ReqQuestionMcOptionDTO> options = request.getMcOptions();
            if (options == null || options.size() != 4) {
                throw new IdInvalidException("MCQ question must contain exactly 4 options A, B, C, D");
            }
            Set<String> optionKeys = options.stream()
                    .map(item -> item.getOptionKey().trim().toUpperCase())
                    .collect(Collectors.toSet());
            if (!optionKeys.equals(Set.of("A", "B", "C", "D"))) {
                throw new IdInvalidException("MCQ question must contain option keys A, B, C, D exactly once");
            }
            if (request.getTfStatements() != null && !request.getTfStatements().isEmpty()) {
                throw new IdInvalidException("MCQ question must not contain true/false statements");
            }
        }

        if (request.getQuestionType() == QuestionType.TFQ) {
            List<ReqQuestionTrueFalseStatementDTO> statements = request.getTfStatements();
            if (statements == null || statements.size() != 4) {
                throw new IdInvalidException("TFQ question must contain exactly 4 statements");
            }
            Set<Integer> statementOrders = statements.stream()
                    .map(ReqQuestionTrueFalseStatementDTO::getStatementOrder)
                    .collect(Collectors.toSet());
            if (!statementOrders.equals(Set.of(1, 2, 3, 4))) {
                throw new IdInvalidException("TFQ statement order must be 1, 2, 3, 4 exactly once");
            }
            if (request.getMcOptions() != null && !request.getMcOptions().isEmpty()) {
                throw new IdInvalidException("TFQ question must not contain MCQ options");
            }
        }

        if (request.getQuestionType() == QuestionType.SAQ) {
            if (request.getMcOptions() != null && !request.getMcOptions().isEmpty()) {
                throw new IdInvalidException("SAQ question must not contain MCQ options");
            }
            if (request.getTfStatements() != null && !request.getTfStatements().isEmpty()) {
                throw new IdInvalidException("SAQ question must not contain true/false statements");
            }
        }

        normalizeAnswer(request.getQuestionType(), request.getAnswerKey().getCorrectAnswerRaw());
    }

    private String normalizeAnswer(QuestionType questionType, String rawAnswer) {
        String sanitized = rawAnswer == null ? "" : rawAnswer.trim().toUpperCase();
        if (!StringUtils.hasText(sanitized)) {
            throw new IdInvalidException("Answer key must not be blank");
        }

        return switch (questionType) {
            case MCQ -> normalizeMcqAnswer(sanitized);
            case TFQ -> normalizeTfqAnswer(sanitized);
            case SAQ -> normalizeSaqAnswerKey(rawAnswer);
        };
    }

    private String normalizeMcqAnswer(String rawAnswer) {
        String compact = rawAnswer.replaceAll("[^A-D]", "");
        if (!StringUtils.hasText(compact)) {
            throw new IdInvalidException("MCQ answer key must only contain A, B, C, D");
        }
        Set<String> uniqueKeys = compact.chars()
                .mapToObj(value -> String.valueOf((char) value))
                .collect(Collectors.toSet());
        return uniqueKeys.stream()
                .sorted()
                .collect(Collectors.joining());
    }

    private String normalizeTfqAnswer(String rawAnswer) {
        String compact = rawAnswer.replaceAll("[^DSN]", "");
        if (compact.length() != 4) {
            throw new IdInvalidException("TFQ answer key must contain exactly 4 characters using D, S, or N");
        }
        return compact;
    }

    private String normalizeSaqAnswerKey(String rawAnswer) {
        List<String> normalizedAnswers = java.util.Arrays.stream(rawAnswer.split(";"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(this::normalizeSingleSaqValue)
                .collect(Collectors.toList());

        if (normalizedAnswers.isEmpty()) {
            throw new IdInvalidException("SAQ answer key must contain at least one valid answer");
        }

        return String.join(";", new LinkedHashSet<>(normalizedAnswers));
    }

    private String normalizeSingleSaqValue(String rawAnswer) {
        String sanitized = rawAnswer == null ? "" : rawAnswer.trim();
        if (!StringUtils.hasText(sanitized)) {
            throw new IdInvalidException("SAQ answer must not be blank");
        }
        if (sanitized.length() < 1 || sanitized.length() > 4) {
            throw new IdInvalidException("SAQ answer must contain from 1 to 4 characters");
        }

        int minusCount = 0;
        int commaCount = 0;
        for (int i = 0; i < sanitized.length(); i++) {
            char currentChar = sanitized.charAt(i);
            if (Character.isDigit(currentChar)) {
                continue;
            }
            if (currentChar == '-') {
                minusCount++;
                if (minusCount > 1 || i != 0) {
                    throw new IdInvalidException("SAQ answer may only contain '-' at the first position");
                }
                continue;
            }
            if (currentChar == ',') {
                commaCount++;
                if (commaCount > 1 || (i != 1 && i != 2)) {
                    throw new IdInvalidException("SAQ answer may only contain ',' at the second or third position");
                }
                continue;
            }
            throw new IdInvalidException("SAQ answer may only contain digits, '-', and ','");
        }

        return padRightWithUnderscore(sanitized);
    }

    private String padRightWithUnderscore(String value) {
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < 4) {
            builder.append('_');
        }
        return builder.toString();
    }

    private UUID resolveCurrentUserUuid() {
        return SecurityUtil.getCurrentUserUuid()
                .map(UUID::fromString)
                .orElseThrow(() -> new IdInvalidException("User id is missing from JWT"));
    }

    private ResQuestionDTO buildResponse(Question question) {
        List<ResQuestionMcOptionDTO> mcOptions = questionMcOptionRepository
                .findByQuestionUuid(question.getQuestionUuid())
                .stream()
                .sorted(Comparator.comparing(QuestionMcOption::getOptionKey))
                .map(item -> ResQuestionMcOptionDTO.builder()
                        .optionUuid(item.getOptionUuid())
                        .optionKey(item.getOptionKey())
                        .optionContent(item.getOptionContent())
                        .build())
                .toList();

        List<ResQuestionTrueFalseStatementDTO> tfStatements = questionTrueFalseStatementRepository
                .findByQuestionUuidOrderByStatementOrderAsc(question.getQuestionUuid())
                .stream()
                .map(item -> ResQuestionTrueFalseStatementDTO.builder()
                        .statementUuid(item.getStatementUuid())
                        .statementOrder(item.getStatementOrder())
                        .statementContent(item.getStatementContent())
                        .build())
                .toList();

        QuestionAnswerKey answerKey = questionAnswerKeyRepository.findByQuestionUuid(question.getQuestionUuid())
                .orElse(null);

        return ResQuestionDTO.builder()
                .questionUuid(question.getQuestionUuid())
                .gradeId(question.getGradeId())
                .questionContent(question.getQuestionContent())
                .questionTopic(question.getQuestionTopic())
                .questionType(question.getQuestionType())
                .createdByUserUuid(question.getCreatedByUserUuid())
                .isActive(question.getIsActive())
                .correctAnswerRaw(answerKey != null ? answerKey.getCorrectAnswerRaw() : null)
                .normalizedAnswer(answerKey != null ? answerKey.getNormalizedAnswer() : null)
                .mcOptions(mcOptions)
                .tfStatements(tfStatements)
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .createdBy(question.getCreatedBy())
                .updatedBy(question.getUpdatedBy())
                .build();
    }
}
