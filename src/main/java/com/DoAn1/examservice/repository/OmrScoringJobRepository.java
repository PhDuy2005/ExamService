package com.DoAn1.examservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.DoAn1.examservice.domain.entity.OmrScoringJob;

public interface OmrScoringJobRepository extends JpaRepository<OmrScoringJob, UUID> {
}
