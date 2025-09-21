package org.nobilis.nobichat.dto.ontology; // Замените на ваш пакет

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
public class OntologyDto {

    private Map<String, EntitySchema> entities = new HashMap<>();

    @Data
    @NoArgsConstructor
    public static class EntitySchema {
        private Meta meta;
        private List<FieldSchema> fields = new ArrayList<>();
        private Map<String, RelationSchema> relations = new HashMap<>();

        @Data
        @NoArgsConstructor
        public static class FieldSchema {
            private String name;
            private String type;
            private String description;
            private String userFriendlyName;
            private List<String> synonyms = new ArrayList<>();
            private DbInfo db;
            private Permissions permissions;

            @JsonProperty("isQueryable")
            private boolean queryable;

            @JsonProperty("isDefaultInList")
            private boolean defaultInList;

            @JsonProperty("isMandatoryInList")
            private boolean mandatoryInList;

            @JsonProperty("isDefaultInCard")
            private boolean defaultInCard;

            @JsonProperty("listComponentId")
            private UUID listComponentId;

            @JsonProperty("formComponentId")
            private UUID formComponentId;

            @Data
            @NoArgsConstructor
            public static class DbInfo {
                private String table;
                private String column;
                private Boolean isPrimaryKey = false;
                private String relationName;
            }
        }

        @Data
        @NoArgsConstructor
        public static class RelationSchema {
            private String type;
            private String targetEntity;
            private List<String> synonyms = new ArrayList<>();
            private String sourceTable;
            private String sourceColumn;
            private String targetTable;
            private String targetColumn;
            private String joinCondition;
            private String fetchStrategy;
            private String label;
            private String tabId;
            @JsonProperty("isDefaultInCard")
            private boolean defaultInCard;
            private int displaySequence;
            private UUID componentId;
        }
    }

    @Data
    @NoArgsConstructor
    public static class Meta {
        private String userFriendlyName;
        private String userFriendlyNameAccusative;
        private String userFriendlyNamePlural;
        private String userFriendlyNamePluralGenitive;
        private String entityNamePlural;
        private String description;
        private String primaryTable;
        private String defaultSearchField;
        private List<String> synonyms = new ArrayList<>();
        private Permissions permissions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Permissions {
        @JsonProperty("read")
        private boolean read = false;
        @JsonProperty("write")
        private boolean write = false;
    }

}