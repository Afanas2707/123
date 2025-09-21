package org.nobilis.nobichat.ontology;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.model.Ontology;
import org.nobilis.nobichat.model.ontology.OntologyEntityDefinition;
import org.nobilis.nobichat.model.ontology.OntologyEntitySynonym;
import org.nobilis.nobichat.repository.OntologyStorageRepository;
import org.nobilis.nobichat.repository.ontology.OntologyEntityRepository;
import org.nobilis.nobichat.service.OntologyJsonToRelationalMigrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@SpringBootTest
class OntologyMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nobichat")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("db/init.sql");

    @DynamicPropertySource
    static void configureDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("ontology.migration.enabled", () -> "false");
    }

    @Autowired
    private OntologyStorageRepository ontologyStorageRepository;

    @Autowired
    private OntologyEntityRepository ontologyEntityRepository;

    @Autowired
    private OntologyJsonToRelationalMigrator migrator;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void migratorMovesLegacyJsonIntoRelationalTables() throws IOException {
        OntologyDto dto = objectMapper.readValue(new ClassPathResource("ontology-simplified.json").getInputStream(),
                OntologyDto.class);
        Ontology ontology = new Ontology();
        ontology.setSchema(dto);
        ontologyStorageRepository.save(ontology);

        assertThat(ontologyEntityRepository.count()).isZero();

        migrator.migrate();

        List<OntologyEntityDefinition> entities = ontologyEntityRepository.findAll();
        assertThat(entities).isNotEmpty();

        Optional<OntologyEntityDefinition> customer = ontologyEntityRepository.findByName("customer");
        assertThat(customer).isPresent();
        assertThat(customer.get().getFields()).isNotEmpty();
        List<String> synonyms = customer.get().getSynonyms().stream()
                .map(OntologyEntitySynonym::getValue)
                .collect(Collectors.toList());
        assertThat(synonyms).contains("клиент");
    }
}
