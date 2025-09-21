package org.nobilis.nobichat.model.scenario;

import jakarta.persistence.CascadeType;
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
@Table(name = "scenario_template")
public class ScenarioTemplate extends BusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(name = "name")
    private String name;

    @Column(name = "template_type")
    private String templateType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_component_id")
    private UiComponent rootComponent;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScenarioFieldBinding> fieldBindings = new ArrayList<>();

    @OneToMany(mappedBy = "template")
    private List<ScenarioStep> steps = new ArrayList<>();
}
