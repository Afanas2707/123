package org.nobilis.nobichat.model.ontology;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.model.BusinessEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ontology_field")
public class OntologyField extends BusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private OntologyEntity entity;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type")
    private String type;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "user_friendly_name")
    private String userFriendlyName;

    @Embedded
    private FieldDbMetadata dbMetadata = new FieldDbMetadata();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_schema", columnDefinition = "jsonb")
    private OntologyDto.UiSchema ui;

    @OneToMany(mappedBy = "field", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OntologyFieldSynonym> synonyms = new ArrayList<>();

    @OneToOne(mappedBy = "field", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private OntologyFieldPermission permission;
}
