package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.ScenarioDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScenarioDraftRepository extends JpaRepository<ScenarioDraft, UUID> {}
