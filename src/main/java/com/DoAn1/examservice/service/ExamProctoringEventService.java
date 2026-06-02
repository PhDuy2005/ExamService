package com.DoAn1.examservice.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.DoAn1.examservice.domain.entity.ExamAttempt;
import com.DoAn1.examservice.domain.entity.ExamProctoringEvent;
import com.DoAn1.examservice.domain.requestDTO.proctoring.ReqProctoringEventBatchDTO;
import com.DoAn1.examservice.domain.requestDTO.proctoring.ReqProctoringEventDTO;
import com.DoAn1.examservice.domain.responseDTO.proctoring.ResProctoringEventBatchDTO;
import com.DoAn1.examservice.domain.responseDTO.proctoring.ResProctoringEventDTO;
import com.DoAn1.examservice.exception.IdInvalidException;
import com.DoAn1.examservice.repository.ExamAttemptRepository;
import com.DoAn1.examservice.repository.ExamProctoringEventRepository;
import com.DoAn1.examservice.util.SecurityUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExamProctoringEventService {

    private final ExamAttemptRepository examAttemptRepository;
    private final ExamProctoringEventRepository examProctoringEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ResProctoringEventDTO> getEvents(UUID attemptUuid) {
        ExamAttempt attempt = examAttemptRepository.findByAttemptUuid(attemptUuid)
                .orElseThrow(() -> new IdInvalidException("Attempt not found with id: " + attemptUuid));
        validateAttemptOwnership(attempt);

        return examProctoringEventRepository.findByAttemptUuidOrderByEventTimeAsc(attemptUuid).stream()
                .map(this::buildResponse)
                .toList();
    }

    @Transactional
    public ResProctoringEventBatchDTO createEvents(UUID attemptUuid, ReqProctoringEventBatchDTO request) {
        ExamAttempt attempt = examAttemptRepository.findByAttemptUuid(attemptUuid)
                .orElseThrow(() -> new IdInvalidException("Attempt not found with id: " + attemptUuid));
        validateAttemptOwnership(attempt);

        List<ExamProctoringEvent> events = request.getEvents().stream()
                .map(item -> buildEvent(attemptUuid, item))
                .toList();
        List<ExamProctoringEvent> savedEvents = examProctoringEventRepository.saveAll(events);

        return ResProctoringEventBatchDTO.builder()
                .attemptUuid(attemptUuid)
                .acceptedCount(savedEvents.size())
                .events(savedEvents.stream()
                        .map(this::buildResponse)
                        .toList())
                .build();
    }

    private ExamProctoringEvent buildEvent(UUID attemptUuid, ReqProctoringEventDTO request) {
        ExamProctoringEvent event = new ExamProctoringEvent();
        event.setAttemptUuid(attemptUuid);
        event.setEventTime(request.getEventTime() != null ? request.getEventTime() : Instant.now());
        event.setEventType(request.getEventType());
        event.setEventPayload(serializePayload(request.getEventPayload()));
        return event;
    }

    private String serializePayload(JsonNode eventPayload) {
        if (eventPayload == null || eventPayload.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException ex) {
            throw new IdInvalidException("Failed to serialize proctoring event payload", ex);
        }
    }

    private ResProctoringEventDTO buildResponse(ExamProctoringEvent event) {
        return ResProctoringEventDTO.builder()
                .eventUuid(event.getEventUuid())
                .attemptUuid(event.getAttemptUuid())
                .eventTime(event.getEventTime())
                .eventType(event.getEventType())
                .eventPayload(event.getEventPayload())
                .build();
    }

    private void validateAttemptOwnership(ExamAttempt attempt) {
        UUID currentStudentUuid = SecurityUtil.getCurrentUserUuid()
                .map(UUID::fromString)
                .orElseThrow(() -> new IdInvalidException("Current user id is required"));
        if (!attempt.getStudentUuid().equals(currentStudentUuid)) {
            throw new IdInvalidException("You do not have permission to access this attempt");
        }
    }
}
