package org.nobilis.nobichat.repository.scenario;

import org.nobilis.nobichat.model.scenario.ScenarioTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScenarioTransitionRepository extends JpaRepository<ScenarioTransition, UUID> {

    List<ScenarioTransition> findByScenarioId(UUID scenarioId);
}
