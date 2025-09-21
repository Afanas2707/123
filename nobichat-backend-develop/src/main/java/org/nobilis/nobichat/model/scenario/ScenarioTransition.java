package org.nobilis.nobichat.model.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.nobilis.nobichat.model.BusinessEntity;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scenario_transition")
public class ScenarioTransition extends BusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_step_id")
    private ScenarioStep fromStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_step_id")
    private ScenarioStep toStep;

    @Column(name = "condition_expression", columnDefinition = "TEXT")
    private String conditionExpression;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_config", columnDefinition = "jsonb")
    private JsonNode conditionConfig;
}
