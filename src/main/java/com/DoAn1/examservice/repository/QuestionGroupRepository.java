package com.DoAn1.examservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.DoAn1.examservice.domain.entity.QuestionGroup;

public interface QuestionGroupRepository extends JpaRepository<QuestionGroup, UUID>, JpaSpecificationExecutor<QuestionGroup> {
    Optional<QuestionGroup> findByQuestionGroupUuid(UUID questionGroupUuid);
}
