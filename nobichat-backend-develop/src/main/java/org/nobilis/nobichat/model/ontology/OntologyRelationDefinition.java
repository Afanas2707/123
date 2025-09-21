package org.nobilis.nobichat.model.ontology;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.nobilis.nobichat.model.BusinessEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ontology_relation")
public class OntologyRelationDefinition extends BusinessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private OntologyEntityDefinition entity;

    @Column(name = "relation_name", nullable = false)
    private String name;

    @Column(name = "relation_type")
    private String type;

    @Column(name = "target_entity_name")
    private String targetEntityName;

    @Column(name = "source_table")
    private String sourceTable;

    @Column(name = "source_column")
    private String sourceColumn;

    @Column(name = "target_table")
    private String targetTable;

    @Column(name = "target_column")
    private String targetColumn;

    @Column(name = "join_condition", columnDefinition = "TEXT")
    private String joinCondition;

    @Column(name = "fetch_strategy")
    private String fetchStrategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_schema", columnDefinition = "jsonb")
    private JsonNode uiSchema;

    @OneToMany(mappedBy = "relation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OntologyRelationSynonym> synonyms = new ArrayList<>();
}
