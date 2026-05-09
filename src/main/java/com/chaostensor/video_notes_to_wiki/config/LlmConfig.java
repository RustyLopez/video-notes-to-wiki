package com.chaostensor.video_notes_to_wiki.config;

import com.chaostensor.video_notes_to_wiki.service.OllamaModelInfoService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.llm") // I don't love configuration properties. It's hard to read. I much prefer @value annotations. Rework this
@Data
@RequiredArgsConstructor
public class LlmConfig {
    private final OllamaModelInfoService ollamaModelInfoService;

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    /**
     * This is a fallback for how large to make individual chunks of a transcript.
     *
     * Semantic chunking is used first, but if the chunks are still to big, they need to be capped to this length.
     *
     * Keep in mind that the purpose of chunking is to be able tos elect a subset of chunks to fit into a tight context window.
     *
     * Which means that this has to be a fraction of the total tokens available in the context window for input, and keep in mind
     * that context window has to support the output tokens and the prompt tokens as well, and the outputs from these prompts
     * will be fairly large. Ideally saturating large portions of the available window.
     *
     * Importantly we want to be able to select teh very best chunks to go into the context. The most relevant. And if
     * chunk size is too big, then we'll be forced to bring in a lot of extra bloat.
     *
     * SO, we want it small enough that we have some degree of granularity on what we select. But not so small that we are splitting semantically associated information unnecessarily.
     *
     * I think a reasonable max size then is 1% of the context window length. This will split within semantically contiguous bounds
     * for smaller models, but will automatically scale up to exceed most semantically contiguous regions fo text as your model capacity grows.
     *
     * So I think that works.
     */
    private int maxTokensForTranscriptChunksForSufficientMultiChunkInclusionGranularity;
    private int threadPoolSize = 5;

    public int getContextWindowTokens() {
        return ollamaModelInfoService.getContextWindowTokens();
    }

    public int getMaxTokensForTranscriptChunksForSufficientMultiChunkInclusionGranularity() {
        return (int) (getContextWindowTokens() * 0.01);
    }

    public String getUrl() {
        return ollamaBaseUrl;
    }

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
    private int promptOverheadTokens = 500; // Estimate for prompt + result buffer ( todo as a percent of total context window )
}