/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.model;

import com.google.cloud.vertexai.api.Schema;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import org.tarik.ta.AgentConfig;
import java.util.List;
import static java.util.Collections.singletonList;

import static dev.langchain4j.model.chat.request.ResponseFormat.JSON;
import static dev.langchain4j.model.chat.request.ResponseFormat.TEXT;
import static org.tarik.ta.AgentConfig.*;

public class ModelFactory {
    private static final String INSTRUCTION_MODEL_NAME = getInstructionModelName();
    private static final String VISION_MODEL_NAME = getVisionModelName();
    private static final int MAX_RETRIES = getMaxRetries();
    private static final int MAX_OUTPUT_TOKENS = getMaxOutputTokens();
    private static final double TEMPERATURE = getTemperature();
    private static final double TOP_P = getTopP();
    private static final ModelProvider MODEL_PROVIDER = AgentConfig.getModelProvider();
    private static final boolean LOG_MODEL_OUTPUTS = isModelLoggingEnabled();
    private static final boolean OUTPUT_THOUGHTS = isThinkingOutputEnabled();

    public static GenAiModel getInstructionModel(boolean outputJson) {
        return switch (MODEL_PROVIDER) {
            case GOOGLE -> new GenAiModel(getGeminiModel(INSTRUCTION_MODEL_NAME, outputJson, LOG_MODEL_OUTPUTS, OUTPUT_THOUGHTS));
            case OPENAI -> new GenAiModel(getOpenAiModel(INSTRUCTION_MODEL_NAME));
        };
    }

    public static GenAiModel getVisionModel(boolean outputJson) {
        return switch (MODEL_PROVIDER) {
            case GOOGLE -> new GenAiModel(getGeminiModel(VISION_MODEL_NAME, outputJson, LOG_MODEL_OUTPUTS, OUTPUT_THOUGHTS));
            case OPENAI -> new GenAiModel(getOpenAiModel(VISION_MODEL_NAME));
        };
    }

    private static ChatModel getGeminiModel(String modelName, boolean outputJson, boolean logResponses, boolean outputThoughts) {
        var provider = AgentConfig.getGoogleApiProvider();
        return switch (provider) {
            case STUDIO_AI -> GoogleAiGeminiChatModel.builder()
                    .apiKey(getGoogleApiToken())
                    .modelName(modelName)
                    .maxRetries(MAX_RETRIES)
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .temperature(TEMPERATURE)
                    .topP(TOP_P)
                    .logRequestsAndResponses(logResponses)
                    //.responseFormat(outputJson ? JSON : TEXT)
                    .thinkingConfig(GeminiThinkingConfig.builder()
                            .includeThoughts(outputThoughts)
                            .thinkingBudget(20000)
                            .build())
                    .listeners(singletonList(new ChatModelEventListener()))
                    .build();

            case VERTEX_AI -> VertexAiGeminiChatModel.builder()
                    .project(getGoogleProject())
                    .location(getGoogleLocation())
                    .modelName(modelName)
                    .maxRetries(MAX_RETRIES)
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .temperature((float) TEMPERATURE)
                    .topP((float) TOP_P)
                    .logResponses(logResponses)
                    .listeners(singletonList(new ChatModelEventListener()))
                    .build();
        };
    }

    private static ChatModel getOpenAiModel(String modelName) {
        return AzureOpenAiChatModel.builder()
                .maxRetries(MAX_RETRIES)
                .apiKey(AgentConfig.getOpenAiApiKey())
                .deploymentName(modelName)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .endpoint(getOpenAiEndpoint())
                .temperature(TEMPERATURE)
                .topP(TOP_P)
                .listeners(singletonList(new ChatModelEventListener()))
                .build();
    }
}
