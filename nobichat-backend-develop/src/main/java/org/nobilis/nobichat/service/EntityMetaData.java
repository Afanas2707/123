package org.nobilis.nobichat.service;

import lombok.Builder;
import lombok.Getter;
import org.nobilis.nobichat.dto.ontology.OntologyDto;

import java.util.List;

@Getter
@Builder
public class EntityMetaData {
    private final String entityName;
    private final String viewTitle;
    private final String viewDescription;
    private final String userFriendlyName;
    private final String userFriendlyNameAccusative;
    private final String userFriendlyNameAccusativeLowercase;
    private final String userFriendlyNamePlural;
    private final String userFriendlyNamePluralGenitive;
    private final String userFriendlyNameLowercase;
    private final OntologyDto.Permissions permissions;
    private final String defaultSearchField;
    private final List<String> synonyms;
}