package com.chaostensor.video_notes_to_wiki.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.llm") // I don't love configuration properties. It's hard to read. I much prefer @value annotations. Rework this
@Data
public class LlmConfig {
    private String url = "http://localhost:8080/v1";
    private int contextWindowTokens = 4096; // Default for GPT-3.5 TODO pull from model config. We'll have a list of models with their corresponding limits.
    private int maxChunkTokens = 4096;
    /**
     * leave room for the "most relevant embeddings" sourced rag result that will also be included in the wiki generation step. This needs to fit along side that,
     * as well as the prompt, and the output buffer. We'll have to... do some more analysis on a proper value to use here, and probably lock down
     * the other components as well so we don't risk randomly failing in prod. Also  fi the result isn't actually this size,
     * we'll have to do an arbitrary prune of the data. Because if we don't enforce this ratio in the result at each layer,
     * then while subsequent layers may produce further reduction, the final layer would still be possibly emitting a value
     * that takes up a higher portion fo the integration LLM's supported input context than we are allowed to occupy.
     *
     * BTW  may be useful having the "allowed occupancy" property be configurable for each of these event handler.
     * Maybe more formally declare them as steps. And the idea is theres a declared understanding that anything they produce,
     * will go into the prompt at the next level, and there fore we need to declare a promise that we will not go above
     * a certain usage. Then if at any layer, the allowed occupancy of the prior step result, plus any rag supplied context,
     * ( which we'll constrain to a fixed length either at generation time or by truncating the resulting chunks ),
     * plus any prompt template, plus the length of expected output data we expect to generate form the ui, plus the expected
     * output buffer size, if all of that exceeds the max prompt context token length then we throw an error at app startup time that
     * the configuration is not valid and will produce context overflows.  We can do this at startup because all of those values
     * would be known and discoverable by declarations, except the output size.But the output will always just be
     * trimmed by the llm to the max context window length so we just allow it to do that.
     */
    private float ragProducedMasterExecutiveSummaryMaxContextOccupationRatio = 0.3f;
    private float hierarchicalSummaryStrategyConfigsPerLayerReductionRatio = 0.3f;
    private int threadPoolSize = 5;
    private long maxMemoryUsageMb = 1024; // TODO this wa supposed to be.. discovered not hard coded.
    private int promptOverheadTokens = 500; // Estimate for prompt + result buffer
}