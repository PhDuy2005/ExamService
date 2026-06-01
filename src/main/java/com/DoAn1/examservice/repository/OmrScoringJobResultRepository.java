package com.DoAn1.examservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.DoAn1.examservice.domain.entity.OmrScoringJobResult;
import com.DoAn1.examservice.domain.enums.OmrScoringJobResultStatus;

public interface OmrScoringJobResultRepository extends JpaRepository<OmrScoringJobResult, UUID> {
    List<OmrScoringJobResult> findByJobUuidOrderByPageNumberAsc(UUID jobUuid);

    long countByJobUuid(UUID jobUuid);

    long countByJobUuidAndStatus(UUID jobUuid, OmrScoringJobResultStatus status);
}
