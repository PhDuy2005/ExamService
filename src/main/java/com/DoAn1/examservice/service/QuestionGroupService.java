package com.DoAn1.examservice.service;

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

import com.DoAn1.examservice.domain.entity.Question;
import com.DoAn1.examservice.domain.entity.QuestionGroup;
import com.DoAn1.examservice.domain.entity.QuestionGroupItem;
import com.DoAn1.examservice.domain.requestDTO.questiongroup.ReqCreateQuestionGroupDTO;
import com.DoAn1.examservice.domain.requestDTO.questiongroup.ReqQuestionGroupItemDTO;
import com.DoAn1.examservice.domain.responseDTO.questiongroup.ResQuestionGroupDTO;
import com.DoAn1.examservice.domain.responseDTO.questiongroup.ResQuestionGroupItemDTO;
import com.DoAn1.examservice.domain.responseDTO.questiongroup.ResQuestionGroupQuestionDetailDTO;
import com.DoAn1.examservice.exception.IdInvalidException;
import com.DoAn1.examservice.repository.QuestionGroupItemRepository;
import com.DoAn1.examservice.repository.QuestionGroupRepository;
import com.DoAn1.examservice.repository.QuestionRepository;
import com.DoAn1.examservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionGroupService {

    private final QuestionGroupRepository questionGroupRepository;
    private final QuestionGroupItemRepository questionGroupItemRepository;
    private final QuestionRepository questionRepository;

    @Transactional
    public ResQuestionGroupDTO createQuestionGroup(ReqCreateQuestionGroupDTO request) {
        QuestionGroupValidationContext validationContext = validateQuestionGroupPayload(request);

        QuestionGroup questionGroup = new QuestionGroup();
        questionGroup.setGroupName(request.getGroupName().trim());
        questionGroup.setQuestionType(request.getQuestionType());
        questionGroup.setQuestionTopic(normalizeTopic(request.getQuestionTopic()));
        questionGroup.setQuestionCount(request.getQuestionCount());
        questionGroup.setCreatedByUserUuid(resolveCurrentUserUuid());

        QuestionGroup savedQuestionGroup = questionGroupRepository.save(questionGroup);
        saveQuestionGroupItems(savedQuestionGroup.getQuestionGroupUuid(), request.getItems());
        return buildResponse(savedQuestionGroup, validationContext.questionById());
    }

    @Transactional(readOnly = true)
    public ResQuestionGroupDTO getQuestionGroup(UUID questionGroupUuid) {
        return buildResponse(findQuestionGroupById(questionGroupUuid));
    }

    @Transactional(readOnly = true)
    public Page<ResQuestionGroupDTO> getQuestionGroups(String name, String type, String topic, Pageable pageable) {
        Specification<QuestionGroup> specification = (root, query, cb) -> cb.conjunction();

        if (StringUtils.hasText(name)) {
            specification = specification.and((root, query, cb) -> cb.like(
                    cb.lower(root.get("groupName")),
                    "%" + name.trim().toLowerCase() + "%"));
        }
        if (StringUtils.hasText(type)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("questionType"),
                    parseQuestionType(type)));
        }
        if (StringUtils.hasText(topic)) {
            specification = specification.and((root, query, cb) -> cb.like(
                    cb.lower(root.get("questionTopic")),
                    "%" + topic.trim().toLowerCase() + "%"));
        }

        return questionGroupRepository.findAll(specification, pageable)
                .map(this::buildResponse);
    }

    @Transactional(readOnly = true)
    public QuestionGroupResolvedData resolveExistingQuestionGroup(UUID questionGroupUuid) {
        QuestionGroup questionGroup = findQuestionGroupById(questionGroupUuid);
        List<QuestionGroupItem> items = questionGroupItemRepository.findByQuestionGroupUuid(questionGroupUuid);
        Map<UUID, Question> questionById = fetchQuestionsByIds(items.stream()
                .map(QuestionGroupItem::getQuestionUuid)
                .collect(Collectors.toSet()));
        return new QuestionGroupResolvedData(questionGroup, items, questionById);
    }

    @Transactional
    public QuestionGroupResolvedData createQuestionGroupAndResolve(ReqCreateQuestionGroupDTO request) {
        QuestionGroupValidationContext validationContext = validateQuestionGroupPayload(request);

        QuestionGroup questionGroup = new QuestionGroup();
        questionGroup.setGroupName(request.getGroupName().trim());
        questionGroup.setQuestionType(request.getQuestionType());
        questionGroup.setQuestionTopic(normalizeTopic(request.getQuestionTopic()));
        questionGroup.setQuestionCount(request.getQuestionCount());
        questionGroup.setCreatedByUserUuid(resolveCurrentUserUuid());
        QuestionGroup savedQuestionGroup = questionGroupRepository.save(questionGroup);

        List<QuestionGroupItem> savedItems = saveQuestionGroupItems(savedQuestionGroup.getQuestionGroupUuid(), request.getItems());
        return new QuestionGroupResolvedData(savedQuestionGroup, savedItems, validationContext.questionById());
    }

    private QuestionGroup findQuestionGroupById(UUID questionGroupUuid) {
        return questionGroupRepository.findById(questionGroupUuid)
                .orElseThrow(() -> new IdInvalidException("Question group not found with id: " + questionGroupUuid));
    }

    private QuestionGroupValidationContext validateQuestionGroupPayload(ReqCreateQuestionGroupDTO request) {
        List<ReqQuestionGroupItemDTO> items = request.getItems();
        if (items.isEmpty()) {
            throw new IdInvalidException("Question group must contain at least one item");
        }

        Set<UUID> uniqueQuestionIds = items.stream()
                .map(ReqQuestionGroupItemDTO::getQuestionUuid)
                .collect(Collectors.toSet());
        if (uniqueQuestionIds.size() != items.size()) {
            throw new IdInvalidException("Question ids in a question group must be unique");
        }

        if (request.getQuestionCount().intValue() != items.size()) {
            throw new IdInvalidException("Question count must match the number of group items");
        }

        Map<UUID, Question> questionById = fetchQuestionsByIds(uniqueQuestionIds);
        for (ReqQuestionGroupItemDTO item : items) {
            Question question = questionById.get(item.getQuestionUuid());
            if (question.getQuestionType() != request.getQuestionType()) {
                throw new IdInvalidException("Question group type must match item question type for question id: "
                        + item.getQuestionUuid());
            }
            String normalizedTopic = normalizeTopic(request.getQuestionTopic());
            String questionTopic = normalizeTopic(question.getQuestionTopic());
            if (normalizedTopic != null && !normalizedTopic.equalsIgnoreCase(questionTopic)) {
                throw new IdInvalidException("Question group topic must match item question topic for question id: "
                        + item.getQuestionUuid());
            }
        }

        return new QuestionGroupValidationContext(questionById);
    }

    private Map<UUID, Question> fetchQuestionsByIds(Set<UUID> questionIds) {
        Map<UUID, Question> questionById = questionIds.isEmpty()
                ? Map.of()
                : questionRepository.findAllById(questionIds).stream()
                        .collect(Collectors.toMap(Question::getQuestionUuid, Function.identity()));

        if (questionById.size() != questionIds.size()) {
            Set<UUID> foundIds = questionById.keySet();
            UUID missingId = questionIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .findFirst()
                    .orElse(null);
            throw new IdInvalidException("Question not found with id: " + missingId);
        }
        return questionById;
    }

    private List<QuestionGroupItem> saveQuestionGroupItems(UUID questionGroupUuid, List<ReqQuestionGroupItemDTO> items) {
        List<QuestionGroupItem> questionGroupItems = items.stream()
                .map(item -> {
                    QuestionGroupItem questionGroupItem = new QuestionGroupItem();
                    questionGroupItem.setQuestionGroupUuid(questionGroupUuid);
                    questionGroupItem.setQuestionUuid(item.getQuestionUuid());
                    return questionGroupItem;
                })
                .toList();
        return questionGroupItemRepository.saveAll(questionGroupItems);
    }

    private ResQuestionGroupDTO buildResponse(QuestionGroup questionGroup) {
        List<QuestionGroupItem> items = questionGroupItemRepository.findByQuestionGroupUuid(questionGroup.getQuestionGroupUuid());
        Map<UUID, Question> questionById = fetchQuestionsByIds(items.stream()
                .map(QuestionGroupItem::getQuestionUuid)
                .collect(Collectors.toSet()));
        return buildResponse(questionGroup, questionById);
    }

    private ResQuestionGroupDTO buildResponse(QuestionGroup questionGroup, Map<UUID, Question> questionById) {
        List<ResQuestionGroupItemDTO> items = questionGroupItemRepository.findByQuestionGroupUuid(questionGroup.getQuestionGroupUuid())
                .stream()
                .map(item -> ResQuestionGroupItemDTO.builder()
                        .questionGroupItemUuid(item.getQuestionGroupItemUuid())
                        .questionUuid(item.getQuestionUuid())
                        .questionDetail(buildQuestionDetail(questionById.get(item.getQuestionUuid())))
                        .build())
                .toList();

        return ResQuestionGroupDTO.builder()
                .questionGroupUuid(questionGroup.getQuestionGroupUuid())
                .groupName(questionGroup.getGroupName())
                .questionType(questionGroup.getQuestionType())
                .questionTopic(questionGroup.getQuestionTopic())
                .questionCount(questionGroup.getQuestionCount())
                .createdByUserUuid(questionGroup.getCreatedByUserUuid())
                .items(items)
                .createdAt(questionGroup.getCreatedAt())
                .updatedAt(questionGroup.getUpdatedAt())
                .createdBy(questionGroup.getCreatedBy())
                .updatedBy(questionGroup.getUpdatedBy())
                .build();
    }

    private ResQuestionGroupQuestionDetailDTO buildQuestionDetail(Question question) {
        if (question == null) {
            return null;
        }
        return ResQuestionGroupQuestionDetailDTO.builder()
                .questionUuid(question.getQuestionUuid())
                .questionContent(question.getQuestionContent())
                .questionTopic(question.getQuestionTopic())
                .questionType(question.getQuestionType())
                .build();
    }

    private UUID resolveCurrentUserUuid() {
        return SecurityUtil.getCurrentUserUuid()
                .map(UUID::fromString)
                .orElseThrow(() -> new IdInvalidException("User id is missing from JWT"));
    }

    private String normalizeTopic(String topic) {
        return StringUtils.hasText(topic) ? topic.trim() : null;
    }

    private com.DoAn1.examservice.domain.enums.QuestionType parseQuestionType(String type) {
        try {
            return com.DoAn1.examservice.domain.enums.QuestionType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IdInvalidException("Invalid question type: " + type);
        }
    }

    private record QuestionGroupValidationContext(Map<UUID, Question> questionById) {
    }

    public record QuestionGroupResolvedData(
            QuestionGroup questionGroup,
            List<QuestionGroupItem> items,
            Map<UUID, Question> questionById) {
    }
}
