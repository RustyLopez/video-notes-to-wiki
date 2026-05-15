package com.chaostensor.video_notes_to_wiki.config;

import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Objects;

@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class OllamaTestContainersDefaultConfig {

    /**
     *TODO make sure this is not pulling the model every time but that teh model is already installed. testoffline
     */
    @Bean
    @ServiceConnection
    OllamaContainer ollamaContainer() throws IOException, InterruptedException {
        final OllamaContainer result =  new OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))
                .withExposedPorts(8080)// TODO se eif we can make this random using springs test support for random ports so it's non conflicting. Have to re-map the internal port to the random external.
                // https://java.testcontainers.org/features/reuse/
                .withReuse(true)// Critical, so that we don't pull the model every time.
                .withCreateContainerCmdModifier(
                        cmd -> Objects.requireNonNull(cmd.getHostConfig()).withDeviceRequests(null)
                );

        result.start(); // Do not explicitly call stop.

        final org.testcontainers.containers.Container.ExecResult execResult = result.execInContainer(
                "ollama", "pull", OllamaModel.LLAMA3_2.getName());
        if (execResult.getExitCode() != 0) {
            throw new IOException("Failed to pull model: " + execResult.getStderr());
        }
        return result;
    }
}
