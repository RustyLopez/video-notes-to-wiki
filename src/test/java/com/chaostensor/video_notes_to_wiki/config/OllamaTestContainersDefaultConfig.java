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

import static org.testcontainers.containers.BindMode.READ_WRITE;

@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class OllamaTestContainersDefaultConfig  {

    /**
     *TODO make sure this is not pulling the model every time but that teh model is already installed. testoffline
     */
    @Bean
    @ServiceConnection
    OllamaContainer ollamaContainer() throws IOException, InterruptedException {
        final OllamaContainer result =  new OllamaContainer(DockerImageName.parse("ollama/ollama:latest"))
                .withExposedPorts(11434)// TODO se eif we can make this random using springs test support for random ports so it's non conflicting. Have to re-map the internal port to the random external.
                // https://java.testcontainers.org/features/reuse/
                .withReuse(true)// Critical, so that we don't pull the model every time.
                         // EDIT doesn't actually work unless you set a  property in the host env's home directory which is super obnoxious and invasive and completely out of keeping with common testing patterns of encapsulation
                         // um so for now we'll keep this here, spring will ignore it...
                         // but we'll bind the ollama cache to a persistent dir.
                         // make sure we are set to pull never int he tests as we have already pulled.
                .withFileSystemBind("./ollama-models", "/root/.ollama", READ_WRITE)
                .withCreateContainerCmdModifier(
                        cmd -> Objects.requireNonNull(cmd.getHostConfig()).withDeviceRequests(null)
                );

        result.start(); // Do not explicitly call stop.

        final org.testcontainers.containers.Container.ExecResult execResult = result.execInContainer(
                "ollama", "pull", OllamaModel.LLAMA3_2.getName());
        if (execResult.getExitCode() != 0) {
            throw new IOException("Failed to pull model: " + execResult.getStderr());
        }
        System.getProperties().put("spring.ai.ollama.base-url", result.getEndpoint());
        System.getProperties().put("spring.ai.ollama.chat.model", OllamaModel.LLAMA3_2.getName());
        System.getProperties().put("spring.ai.ollama.init.pull-model-strategy", "never"/* should already be */);


        return result;
    }
}
