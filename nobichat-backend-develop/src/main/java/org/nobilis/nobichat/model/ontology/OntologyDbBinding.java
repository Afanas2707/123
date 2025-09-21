package org.nobilis.nobichat.model.ontology;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ontology_db_binding")
public class OntologyDbBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "column_name")
    private String columnName;

    @Column(name = "is_primary_key")
    private Boolean primaryKey;

    @Column(name = "relation_name")
    private String relationName;
}
