package org.nobilis.nobichat.model.ontology;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.nobilis.nobichat.model.BusinessEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ontology_entity")
public class OntologyEntity extends BusinessEntity {

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

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "primary_table")
    private String primaryTable;

    @Column(name = "default_search_field")
    private String defaultSearchField;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "synonyms", columnDefinition = "jsonb")
    private List<String> synonyms = new ArrayList<>();

    @Column(name = "permission_read")
    private boolean permissionRead;

    @Column(name = "permission_write")
    private boolean permissionWrite;

    @OneToMany(mappedBy = "entity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OntologyField> fields = new ArrayList<>();

    @OneToMany(mappedBy = "sourceEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OntologyRelation> relations = new ArrayList<>();
}
