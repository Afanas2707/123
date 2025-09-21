package org.nobilis.nobichat.repository.scenario;

import org.nobilis.nobichat.model.scenario.ScenarioTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScenarioTemplateRepository extends JpaRepository<ScenarioTemplate, UUID> {

    List<ScenarioTemplate> findByScenarioId(UUID scenarioId);
}
