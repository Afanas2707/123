package org.nobilis.nobichat.model.ontology;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.nobilis.nobichat.model.BusinessEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ontology_entity")
public class OntologyEntityDefinition extends BusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "user_friendly_name")
    private String userFriendlyName;

    @Column(name = "user_friendly_name_accusative")
    private String userFriendlyNameAccusative;

    @Column(name = "user_friendly_name_plural")
    private String userFriendlyNamePlural;

    @Column(name = "user_friendly_name_plural_genitive")
    private String userFriendlyNamePluralGenitive;

    @Column(name = "entity_name_plural")
    private String entityNamePlural;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "primary_table")
    private String primaryTable;

    @Column(name = "default_search_field")
    private String defaultSearchField;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "permissions_id")
    private OntologyPermission permissions;

    @OneToMany(mappedBy = "entity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OntologyEntitySynonym> synonyms = new ArrayList<>();

    @OneToMany(mappedBy = "entity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OntologyFieldDefinition> fields = new ArrayList<>();

    @OneToMany(mappedBy = "entity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OntologyRelationDefinition> relations = new ArrayList<>();
}
