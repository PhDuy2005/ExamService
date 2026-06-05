package com.DoAn1.examservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.DoAn1.examservice.domain.entity.Question;
import com.DoAn1.examservice.domain.entity.QuestionGroup;
import com.DoAn1.examservice.domain.entity.QuestionGroupItem;
import com.DoAn1.examservice.domain.enums.QuestionType;
import com.DoAn1.examservice.domain.requestDTO.questiongroup.ReqQuestionGroupItemDTO;
import com.DoAn1.examservice.domain.requestDTO.questiongroup.ReqUpdateQuestionGroupItemsDTO;
import com.DoAn1.examservice.domain.responseDTO.questiongroup.ResQuestionGroupDTO;
import com.DoAn1.examservice.exception.IdInvalidException;
import com.DoAn1.examservice.repository.QuestionGroupItemRepository;
import com.DoAn1.examservice.repository.QuestionGroupRepository;
import com.DoAn1.examservice.repository.QuestionRepository;

class QuestionGroupServiceTest {

    private final QuestionGroupRepository questionGroupRepository = mock(QuestionGroupRepository.class);
    private final QuestionGroupItemRepository questionGroupItemRepository = mock(QuestionGroupItemRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);

    private QuestionGroupService questionGroupService;
    private UUID questionGroupUuid;
    private QuestionGroup questionGroup;

    @BeforeEach
    void setUp() {
        questionGroupService = new QuestionGroupService(
                questionGroupRepository,
                questionGroupItemRepository,
                questionRepository);

        questionGroupUuid = UUID.randomUUID();
        questionGroup = new QuestionGroup();
        questionGroup.setQuestionGroupUuid(questionGroupUuid);
        questionGroup.setGroupName("Algebra");
        questionGroup.setQuestionType(QuestionType.MCQ);
        questionGroup.setQuestionTopic("Algebra");
        questionGroup.setQuestionCount(1);
        questionGroup.setCreatedByUserUuid(UUID.randomUUID());

        when(questionGroupRepository.findByQuestionGroupUuid(questionGroupUuid)).thenReturn(Optional.of(questionGroup));
        when(questionGroupRepository.save(any(QuestionGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(questionGroupItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateQuestionGroupItemsReplacesItemsAndUpdatesCount() {
        Question firstQuestion = question(UUID.randomUUID(), QuestionType.MCQ, "Algebra");
        Question secondQuestion = question(UUID.randomUUID(), QuestionType.MCQ, "Algebra");
        ReqUpdateQuestionGroupItemsDTO request = updateRequest(firstQuestion.getQuestionUuid(), secondQuestion.getQuestionUuid());

        QuestionGroupItem firstItem = groupItem(firstQuestion.getQuestionUuid());
        QuestionGroupItem secondItem = groupItem(secondQuestion.getQuestionUuid());
        when(questionRepository.findAllById(any())).thenReturn(List.of(firstQuestion, secondQuestion));
        when(questionGroupItemRepository.findByQuestionGroupUuid(questionGroupUuid))
                .thenReturn(List.of(firstItem, secondItem));

        ResQuestionGroupDTO response = questionGroupService.updateQuestionGroupItems(questionGroupUuid, request);

        assertThat(response.getQuestionCount()).isEqualTo(2);
        assertThat(response.getItems()).extracting(item -> item.getQuestionUuid())
                .containsExactly(firstQuestion.getQuestionUuid(), secondQuestion.getQuestionUuid());

        InOrder replacementOrder = inOrder(questionGroupItemRepository);
        replacementOrder.verify(questionGroupItemRepository).deleteByQuestionGroupUuid(questionGroupUuid);
        replacementOrder.verify(questionGroupItemRepository).flush();
        replacementOrder.verify(questionGroupItemRepository).saveAll(any());
        verify(questionGroupRepository).save(questionGroup);
    }

    @Test
    void updateQuestionGroupItemsRejectsQuestionWithDifferentType() {
        Question invalidQuestion = question(UUID.randomUUID(), QuestionType.TFQ, "Algebra");
        ReqUpdateQuestionGroupItemsDTO request = updateRequest(invalidQuestion.getQuestionUuid());
        when(questionRepository.findAllById(any())).thenReturn(List.of(invalidQuestion));

        assertThatThrownBy(() -> questionGroupService.updateQuestionGroupItems(questionGroupUuid, request))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Question group type must match item question type");

        verify(questionGroupItemRepository, never()).deleteByQuestionGroupUuid(any());
        verify(questionGroupRepository, never()).save(any());
    }

    @Test
    void updateQuestionGroupItemsRejectsEmptyList() {
        ReqUpdateQuestionGroupItemsDTO request = new ReqUpdateQuestionGroupItemsDTO();
        request.setItems(List.of());

        assertThatThrownBy(() -> questionGroupService.updateQuestionGroupItems(questionGroupUuid, request))
                .isInstanceOf(IdInvalidException.class)
                .hasMessage("Question group must contain at least one item");

        verify(questionGroupItemRepository, never()).deleteByQuestionGroupUuid(any());
    }

    private ReqUpdateQuestionGroupItemsDTO updateRequest(UUID... questionUuids) {
        ReqUpdateQuestionGroupItemsDTO request = new ReqUpdateQuestionGroupItemsDTO();
        request.setItems(java.util.Arrays.stream(questionUuids)
                .map(questionUuid -> {
                    ReqQuestionGroupItemDTO item = new ReqQuestionGroupItemDTO();
                    item.setQuestionUuid(questionUuid);
                    return item;
                })
                .toList());
        return request;
    }

    private Question question(UUID questionUuid, QuestionType questionType, String questionTopic) {
        Question question = new Question();
        question.setQuestionUuid(questionUuid);
        question.setQuestionContent("Question");
        question.setQuestionType(questionType);
        question.setQuestionTopic(questionTopic);
        return question;
    }

    private QuestionGroupItem groupItem(UUID questionUuid) {
        QuestionGroupItem item = new QuestionGroupItem();
        item.setQuestionGroupItemUuid(UUID.randomUUID());
        item.setQuestionGroupUuid(questionGroupUuid);
        item.setQuestionUuid(questionUuid);
        return item;
    }
}
