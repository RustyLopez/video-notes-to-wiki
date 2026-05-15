package com.chaostensor.video_notes_to_wiki;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;


@Testcontainers
@SpringBootTest
@ActiveProfiles(profiles = "test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VideoNotesToWikiApplicationTests {

	@Container
	static PostgreSQLContainer<?> postgresWithVector = new PostgreSQLContainer<>("pgvector/pgvector:pg18")
			.withDatabaseName("testdb")
			.withUsername("test")
			.withPassword("test");

	@Container
	static OllamaContainer ollama = new OllamaContainer("ollama/ollama:latest");

	@DynamicPropertySource
	static void registerProperties(final DynamicPropertyRegistry registry) {
		registry.add("spring.r2dbc.url", () -> postgresWithVector.getJdbcUrl().replace("jdbc:", "r2dbc:"));
		registry.add("spring.r2dbc.username", postgresWithVector::getUsername);
		registry.add("spring.r2dbc.password", postgresWithVector::getPassword);

		registry.add("spring.datasource.url", postgresWithVector::getJdbcUrl);
		registry.add("spring.datasource.username", postgresWithVector::getUsername);
		registry.add("spring.datasource.password", postgresWithVector::getPassword);
		registry.add("spring.datasource.driver-class-name", ()->"org.postgresql.Driver");

		registry.add("spring.ai.ollama.base-url", ollama::getEndpoint);
		registry.add("spring.ai.ollama.init.pull-model-strategy", () -> "never");
	}


	@Test
	void contextLoads() {
	}
}
