package com.chaostensor.video_notes_to_wiki.event;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class EventHandlerTranscriptsHierarchicalRollupToWikiTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    /**
     * THe ollama container requires special handling
     */
    @Autowired
    private OllamaContainer ollamaContainer;

    @DynamicPropertySource
    static void registerProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.ai.ollama.init.pull-model-strategy", () -> "never"/* should already be */);
        registry.add("app.llm.chat.models.preferred", OllamaModel.LLAMA3_2::getName);
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsAndHandlerBeanExists() {
        assert context.getBean(EventHandlerTranscriptsHierarchicalRollupToWiki.class) != null;
    }
}
