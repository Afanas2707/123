package org.nobilis.nobichat.repository.scenario;

import org.nobilis.nobichat.model.scenario.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {

    Optional<Scenario> findByCode(String code);
}
