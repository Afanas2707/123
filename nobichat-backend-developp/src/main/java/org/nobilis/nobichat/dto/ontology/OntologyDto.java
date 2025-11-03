package org.nobilis.nobichat.dto.ontology; // Замените на ваш пакет

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            private UiSchema ui;
            private Permissions permissions;

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
            private UiRelationSchema ui;
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

    @Data
    @NoArgsConstructor
    public static class UiSchema {
        @JsonProperty("isQueryable")
        private boolean isQueryable;

        @JsonProperty("isDefaultInList")
        private boolean isDefaultInList;
        @JsonProperty("isMandatoryInList")
        private boolean isMandatoryInList;
        @JsonProperty("isDefaultInCard")
        private boolean isDefaultInCard;

        @JsonProperty("ListApplet")
        private ListApplet listApplet;

        private FormView formView;
    }

    @Data
    @NoArgsConstructor
    public static class UiRelationSchema {
        private String label;
        private String tabId;
        @JsonProperty("isDefaultInCard")
        private boolean isDefaultInCard;
        private int displaySequence;
    }

    @Data
    @NoArgsConstructor
    public static class ListApplet {
        private String name;
        private String label;
        private ComponentSchema component;
        private int displaySequence;
        @JsonProperty("isSearchable")
        private boolean isSearchable;
    }

    @Data
    @NoArgsConstructor
    public static class FormView {
        private String component;
        private String label;
        private String section;
        private int displaySequence;

        @JsonProperty("isCardHeader")
        private boolean cardHeader;

        @JsonProperty("isControl")
        private boolean control;

        private boolean required;
        private boolean disabled;
        private ActionSchema action;
        private List<ComponentSchema> components = new ArrayList<>();

        private String dataType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentSchema {
        private String type;
        private String dataType;
        private String fieldName;
        private ActionSchema action;
        private List<ComponentSchema> components = new ArrayList<>();

        private boolean required;
        private boolean disabled;
    }

    @Data
    @NoArgsConstructor
    public static class ActionSchema {
        private String type;
        private String method;
        private String endpoint;
        private Map<String, String> params;
        private String formId;
        private String modalId;
        private String variant;
    }
}