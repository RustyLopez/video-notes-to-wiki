package com.chaostensor.video_notes_to_wiki.config;

import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.Container;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Objects;

@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class OllamaTestContainersDefaultConfig {

    /**
     *
     */
    @Bean
    @ServiceConnection
    OllamaContainer ollamaContainer() throws IOException, InterruptedException {
        final OllamaContainer result =  new OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))
                .withCreateContainerCmdModifier(
                        cmd -> Objects.requireNonNull(cmd.getHostConfig()).withDeviceRequests(null)
                );

        result.start();

        final Container.ExecResult execResult = result.execInContainer(
                "ollama", "pull", OllamaModel.LLAMA3_2.getName());
        if (execResult.getExitCode() != 0) {
            throw new IOException("Failed to pull model: " + execResult.getStderr());
        }
        return result;
    }
}
