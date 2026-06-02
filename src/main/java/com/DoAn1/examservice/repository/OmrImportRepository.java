package com.DoAn1.examservice.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.DoAn1.examservice.domain.entity.OmrImport;

public interface OmrImportRepository extends JpaRepository<OmrImport, UUID> {
    List<OmrImport> findByExamUuid(UUID examUuid);

    List<OmrImport> findByAttemptUuidIn(List<UUID> attemptUuids);

    Optional<OmrImport> findByExternalSubmissionId(String externalSubmissionId);

    boolean existsByExternalSubmissionId(String externalSubmissionId);
}
