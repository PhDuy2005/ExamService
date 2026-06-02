package com.DoAn1.examservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.DoAn1.examservice.domain.entity.ExamAttempt;
import com.DoAn1.examservice.domain.enums.AttemptStatus;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {
    List<ExamAttempt> findByExamUuid(UUID examUuid);

    List<ExamAttempt> findByExamUuidAndStudentUuidOrderByAttemptNoAsc(UUID examUuid, UUID studentUuid);

    Optional<ExamAttempt> findTopByExamUuidAndStudentUuidOrderByAttemptNoDesc(UUID examUuid, UUID studentUuid);

    List<ExamAttempt> findByStudentUuidOrderByCreatedAtDesc(UUID studentUuid);

    Page<ExamAttempt> findByStudentUuid(UUID studentUuid, Pageable pageable);

    Page<ExamAttempt> findByStudentUuidAndExamUuid(UUID studentUuid, UUID examUuid, Pageable pageable);

    List<ExamAttempt> findByStatusOrderByStartedAtAsc(AttemptStatus status);
}

