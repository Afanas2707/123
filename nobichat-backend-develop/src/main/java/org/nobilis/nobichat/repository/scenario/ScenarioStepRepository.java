package org.nobilis.nobichat.repository.scenario;

import org.nobilis.nobichat.model.scenario.ScenarioStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScenarioStepRepository extends JpaRepository<ScenarioStep, UUID> {

    List<ScenarioStep> findByScenarioId(UUID scenarioId);
}
