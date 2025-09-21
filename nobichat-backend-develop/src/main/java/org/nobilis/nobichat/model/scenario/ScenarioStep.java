package org.nobilis.nobichat.model.scenario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nobilis.nobichat.model.BusinessEntity;
import org.nobilis.nobichat.model.ui.UiComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scenario_step")
public class ScenarioStep extends BusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private ScenarioTemplate template;

    @Column(name = "name")
    private String name;

    @Column(name = "label")
    private String label;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order")
    private Integer displayOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_component_id")
    private UiComponent entryComponent;

    @OneToMany(mappedBy = "fromStep")
    private List<ScenarioTransition> outgoingTransitions = new ArrayList<>();

    @OneToMany(mappedBy = "toStep")
    private List<ScenarioTransition> incomingTransitions = new ArrayList<>();
}
