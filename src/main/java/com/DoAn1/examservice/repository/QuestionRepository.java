package com.DoAn1.examservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.DoAn1.examservice.domain.entity.Question;

public interface QuestionRepository extends JpaRepository<Question, UUID>, JpaSpecificationExecutor<Question> {
    Optional<Question> findByQuestionUuid(UUID questionUuid);
}
