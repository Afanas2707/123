package org.nobilis.nobichat.model.ontology;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class FieldDbMetadata {

    @Column(name = "db_table")
    private String tableName;

    @Column(name = "db_column")
    private String columnName;

    @Column(name = "is_primary_key")
    private boolean primaryKey;

    @Column(name = "relation_name")
    private String relationName;
}
