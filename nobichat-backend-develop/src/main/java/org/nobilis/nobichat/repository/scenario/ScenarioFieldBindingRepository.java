package org.nobilis.nobichat.repository.scenario;

import org.nobilis.nobichat.model.scenario.ScenarioFieldBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScenarioFieldBindingRepository extends JpaRepository<ScenarioFieldBinding, UUID> {

    List<ScenarioFieldBinding> findByTemplateId(UUID templateId);
}
