package org.nobilis.nobichat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.nobilis.nobichat.dto.ontology.EntityMetaData;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.feign.ConfluenceClient;
import org.nobilis.nobichat.model.Ontology;
import org.nobilis.nobichat.repository.OntologyStorageRepository;
import org.nobilis.nobichat.repository.ontology.OntologyEntityRepository;
import org.nobilis.nobichat.repository.ontology.OntologyFieldRepository;
import org.nobilis.nobichat.repository.ontology.OntologyRelationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OntologyServiceTest {

    @Autowired
    private OntologyStorageRepository ontologyStorageRepository;

    @Autowired
    private OntologyEntityRepository ontologyEntityRepository;

    @Autowired
    private OntologyFieldRepository ontologyFieldRepository;

    @Autowired
    private OntologyRelationRepository ontologyRelationRepository;

    private OntologyService ontologyService;

    @BeforeEach
    void setUp() {
        ontologyService = new OntologyService(
                ontologyStorageRepository,
                ontologyEntityRepository,
                ontologyFieldRepository,
                ontologyRelationRepository,
                new ObjectMapper(),
                Mockito.mock(ConfluenceClient.class)
        );
    }

    @Test
    void updateAndReadOntologyFromNormalizedSchema() {
        OntologyDto ontologyDto = buildSampleOntology();

        OntologyDto persisted = ontologyService.updateOntology(ontologyDto);

        assertThat(ontologyEntityRepository.count()).isEqualTo(2);
        assertThat(ontologyFieldRepository.count()).isEqualTo(2);
        assertThat(ontologyRelationRepository.count()).isEqualTo(1);

        OntologyDto.EntitySchema productSchema = ontologyService.getEntitySchema("product");
        assertThat(productSchema.getFields()).hasSize(2);
        assertThat(productSchema.getRelations()).containsKey("orders");

        OntologyDto.EntitySchema.FieldSchema nameField = productSchema.getFields().stream()
                .filter(field -> field.getName().equals("name"))
                .findFirst()
                .orElseThrow();
        assertThat(nameField.getDb().getTable()).isEqualTo("product_table");
        assertThat(nameField.getDb().getColumn()).isEqualTo("name");
        assertThat(nameField.getPermissions()).isNotNull();
        assertThat(nameField.getPermissions().isWrite()).isTrue();
        assertThat(nameField.getSynonyms()).contains("title");
        assertThat(nameField.getUi()).isNotNull();
        assertThat(nameField.getUi().isQueryable()).isTrue();
        assertThat(nameField.getUi().getListApplet()).isNotNull();
        assertThat(nameField.getUi().getListApplet().isSearchable()).isTrue();

        OntologyDto.EntitySchema.FieldSchema idField = productSchema.getFields().stream()
                .filter(field -> field.getName().equals("id"))
                .findFirst()
                .orElseThrow();
        assertThat(idField.getDb().getIsPrimaryKey()).isTrue();
        assertThat(idField.getPermissions()).isNotNull();
        assertThat(idField.getPermissions().isRead()).isTrue();
        assertThat(idField.getPermissions().isWrite()).isFalse();

        OntologyDto.EntitySchema.RelationSchema ordersRelation = productSchema.getRelations().get("orders");
        assertThat(ordersRelation).isNotNull();
        assertThat(ordersRelation.getTargetEntity()).isEqualTo("order");
        assertThat(ordersRelation.getSynonyms()).contains("orders");
        assertThat(ordersRelation.getSourceTable()).isEqualTo("product_table");
        assertThat(ordersRelation.getTargetColumn()).isEqualTo("product_id");
        assertThat(ordersRelation.getUi()).isNotNull();
        assertThat(ordersRelation.getUi().isDefaultInCard()).isTrue();

        assertThat(ontologyService.entityExists("product")).isTrue();
        assertThat(ontologyService.entityExists("unknown")).isFalse();

        List<String> searchableFields = ontologyService.getSearchableFieldsForUI("product");
        assertThat(searchableFields).containsExactly("name");

        List<OntologyDto.EntitySchema.FieldSchema> queryableFields = ontologyService.getAllQueryableFields();
        assertThat(queryableFields).extracting(OntologyDto.EntitySchema.FieldSchema::getName)
                .containsExactly("name");

        EntityMetaData metaData = ontologyService.getEntityMetaData("product");
        assertThat(metaData.getUserFriendlyName()).isEqualTo("Product");
        assertThat(metaData.getUserFriendlyNamePlural()).isEqualTo("Products");
        assertThat(metaData.getSynonyms()).containsExactly("item");
        assertThat(metaData.getPermissions().isRead()).isTrue();
        assertThat(metaData.getPermissions().isWrite()).isFalse();
        assertThat(metaData.getDefaultSearchField()).isEqualTo("name");

        Map<String, EntityMetaData> promptMeta = ontologyService.getAllEntityMetaForPrompt();
        assertThat(promptMeta).containsKey("product");
        assertThat(promptMeta.get("product").getSynonyms()).containsExactly("item");

        OntologyDto currentSchema = ontologyService.getCurrentOntologySchema();
        assertThat(currentSchema.getEntities()).containsKey("product");
        assertThat(currentSchema.getEntities().get("product").getFields()).hasSize(2);

        // Ensure JSON storage updated for backward compatibility
        Ontology storedOntology = ontologyStorageRepository.findFirstByOrderByCreationDateDesc().orElse(null);
        assertThat(storedOntology).isNotNull();
        assertThat(storedOntology.getSchema().getEntities()).containsKey("product");

        // Ensure round-trip persists field order-insensitive equality
        assertThat(persisted.getEntities().get("product").getFields()).extracting(OntologyDto.EntitySchema.FieldSchema::getName)
                .containsExactlyInAnyOrder("id", "name");
    }

    private OntologyDto buildSampleOntology() {
        OntologyDto ontologyDto = new OntologyDto();

        OntologyDto.EntitySchema product = new OntologyDto.EntitySchema();
        OntologyDto.Meta productMeta = new OntologyDto.Meta();
        productMeta.setUserFriendlyName("Product");
        productMeta.setUserFriendlyNameAccusative("Product");
        productMeta.setUserFriendlyNamePlural("Products");
        productMeta.setUserFriendlyNamePluralGenitive("products");
        productMeta.setEntityNamePlural("Products");
        productMeta.setDescription("Product description");
        productMeta.setPrimaryTable("product_table");
        productMeta.setDefaultSearchField("name");
        productMeta.setSynonyms(List.of("item"));
        productMeta.setPermissions(new OntologyDto.Permissions(true, false));
        product.setMeta(productMeta);

        OntologyDto.EntitySchema.FieldSchema idField = new OntologyDto.EntitySchema.FieldSchema();
        idField.setName("id");
        idField.setType("uuid");
        idField.setDescription("Identifier");
        idField.setUserFriendlyName("Identifier");
        idField.setSynonyms(List.of("identifier"));
        OntologyDto.EntitySchema.FieldSchema.DbInfo idDb = new OntologyDto.EntitySchema.FieldSchema.DbInfo();
        idDb.setTable("product_table");
        idDb.setColumn("id");
        idDb.setIsPrimaryKey(true);
        idField.setDb(idDb);
        idField.setUi(new OntologyDto.UiSchema());
        idField.getUi().setQueryable(false);
        idField.setPermissions(new OntologyDto.Permissions(true, false));

        OntologyDto.EntitySchema.FieldSchema nameField = new OntologyDto.EntitySchema.FieldSchema();
        nameField.setName("name");
        nameField.setType("string");
        nameField.setDescription("Display name");
        nameField.setUserFriendlyName("Name");
        nameField.setSynonyms(List.of("title"));
        OntologyDto.EntitySchema.FieldSchema.DbInfo nameDb = new OntologyDto.EntitySchema.FieldSchema.DbInfo();
        nameDb.setTable("product_table");
        nameDb.setColumn("name");
        nameDb.setIsPrimaryKey(false);
        nameField.setDb(nameDb);
        OntologyDto.UiSchema nameUi = new OntologyDto.UiSchema();
        nameUi.setQueryable(true);
        OntologyDto.ListApplet listApplet = new OntologyDto.ListApplet();
        listApplet.setSearchable(true);
        listApplet.setName("nameColumn");
        listApplet.setLabel("Name");
        nameUi.setListApplet(listApplet);
        nameField.setUi(nameUi);
        nameField.setPermissions(new OntologyDto.Permissions(true, true));

        product.setFields(new ArrayList<>(List.of(idField, nameField)));

        OntologyDto.EntitySchema.RelationSchema ordersRelation = new OntologyDto.EntitySchema.RelationSchema();
        ordersRelation.setType("one-to-many");
        ordersRelation.setTargetEntity("order");
        ordersRelation.setSynonyms(List.of("orders"));
        ordersRelation.setSourceTable("product_table");
        ordersRelation.setSourceColumn("id");
        ordersRelation.setTargetTable("order_table");
        ordersRelation.setTargetColumn("product_id");
        ordersRelation.setJoinCondition("product_table.id = order_table.product_id");
        ordersRelation.setFetchStrategy("EAGER");
        OntologyDto.UiRelationSchema relationUi = new OntologyDto.UiRelationSchema();
        relationUi.setLabel("Orders");
        relationUi.setDefaultInCard(true);
        relationUi.setDisplaySequence(1);
        ordersRelation.setUi(relationUi);

        product.setRelations(new LinkedHashMap<>());
        product.getRelations().put("orders", ordersRelation);

        OntologyDto.EntitySchema order = new OntologyDto.EntitySchema();
        OntologyDto.Meta orderMeta = new OntologyDto.Meta();
        orderMeta.setUserFriendlyName("Order");
        orderMeta.setUserFriendlyNamePlural("Orders");
        orderMeta.setPermissions(new OntologyDto.Permissions(true, true));
        order.setMeta(orderMeta);
        order.setFields(new ArrayList<>());
        order.setRelations(new LinkedHashMap<>());

        ontologyDto.getEntities().put("product", product);
        ontologyDto.getEntities().put("order", order);

        return ontologyDto;
    }
}
