package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {

    @Query(value = "SELECT EXISTS(SELECT 1 FROM scenarios WHERE definition ->> 'name' = :name)", nativeQuery = true)
    boolean existsByNameInDefinition(@Param("name") String name);

    @Query(value = "SELECT * FROM scenarios WHERE definition ->> 'name' = :name LIMIT 1", nativeQuery = true)
    Optional<Scenario> findByNameInDefinition(@Param("name") String name);

    @Query(value = "SELECT definition ->> 'name' FROM scenarios", nativeQuery = true)
    List<String> findAllScenarioNames();
}
