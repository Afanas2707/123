package org.nobilis.nobichat.model.ontology;

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
import jakarta.persistence.OneToOne;
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
@Table(name = "ontology_field")
public class OntologyFieldDefinition extends BusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private OntologyEntityDefinition entity;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type")
    private String type;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "user_friendly_name")
    private String userFriendlyName;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "permissions_id")
    private OntologyPermission permissions;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "db_binding_id")
    private OntologyDbBinding dbBinding;

    @Column(name = "is_queryable", nullable = false)
    private boolean queryable;

    @Column(name = "is_default_in_list", nullable = false)
    private boolean defaultInList;

    @Column(name = "is_mandatory_in_list", nullable = false)
    private boolean mandatoryInList;

    @Column(name = "is_default_in_card", nullable = false)
    private boolean defaultInCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_component_id")
    private UiComponent listComponent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_component_id")
    private UiComponent formComponent;

    @OneToMany(mappedBy = "field", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OntologyFieldSynonym> synonyms = new ArrayList<>();
}
