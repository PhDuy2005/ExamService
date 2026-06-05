package com.DoAn1.examservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.DoAn1.examservice.domain.entity.ExamProctoringEvent;

public interface ExamProctoringEventRepository extends JpaRepository<ExamProctoringEvent, UUID> {
    List<ExamProctoringEvent> findByAttemptUuidOrderByEventTimeAsc(UUID attemptUuid);

    long countByAttemptUuid(UUID attemptUuid);
}

