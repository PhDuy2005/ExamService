package com.DoAn1.examservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.DoAn1.examservice.domain.entity.StudentAnswer;

public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, UUID> {
    List<StudentAnswer> findByAttemptUuidAndQuestionUuidOrderByQuestionAttemptNumberAsc(UUID attemptUuid, UUID questionUuid);

    List<StudentAnswer> findByAttemptUuidIn(List<UUID> attemptUuids);

    Optional<StudentAnswer> findByAttemptUuidAndQuestionUuidAndIsFinalAnswerTrue(UUID attemptUuid, UUID questionUuid);

    List<StudentAnswer> findByAttemptUuidOrderByQuestionUuidAscQuestionAttemptNumberAsc(UUID attemptUuid);
}

