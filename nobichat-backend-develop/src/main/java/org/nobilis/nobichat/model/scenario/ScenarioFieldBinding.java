package org.nobilis.nobichat.model.scenario;

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
import org.nobilis.nobichat.model.BusinessEntity;
import org.nobilis.nobichat.model.ontology.OntologyFieldDefinition;
import org.nobilis.nobichat.model.ui.UiComponent;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scenario_field_binding")
public class ScenarioFieldBinding extends BusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ScenarioTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private OntologyFieldDefinition field;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private UiComponent component;

    @Column(name = "binding_type")
    private String bindingType;

    @Column(name = "is_required")
    private Boolean required;

    @Column(name = "display_order")
    private Integer displayOrder;
}
