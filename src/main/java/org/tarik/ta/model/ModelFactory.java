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

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import org.tarik.ta.AgentConfig;

import static org.tarik.ta.AgentConfig.*;

public class ModelFactory {
    private static final String INSTRUCTION_MODEL_NAME = getInstructionModelName();
    private static final String VISION_MODEL_NAME = getVisionModelName();
    private static final int MAX_RETRIES = getMaxRetries();
    private static final int MAX_OUTPUT_TOKENS = getMaxOutputTokens();
    private static final double TEMPERATURE = getTemperature();
    private static final double TOP_P = getTopP();

    public static GenAiModel getInstructionModel() {
        var provider = AgentConfig.getModelProvider();
        return switch (provider) {
            case GOOGLE -> new GenAiModel(getGeminiModel(INSTRUCTION_MODEL_NAME));
            case OPENAI -> new GenAiModel(getOpenAiModel(INSTRUCTION_MODEL_NAME));
        };
    }

    public static GenAiModel getVisionModel() {
        var provider = AgentConfig.getModelProvider();
        return switch (provider) {
            case GOOGLE -> new GenAiModel(getGeminiModel(VISION_MODEL_NAME));
            case OPENAI -> new GenAiModel(getOpenAiModel(VISION_MODEL_NAME));
        };
    }

    private static ChatLanguageModel getGeminiModel(String modelName) {
        var provider = AgentConfig.getGoogleApiProvider();
        return switch (provider) {
            case STUDIO_AI -> GoogleAiGeminiChatModel.builder()
                    .apiKey(getGoogleApiToken())
                    .modelName(modelName)
                    .maxRetries(MAX_RETRIES)
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .temperature(TEMPERATURE)
                    .topP(TOP_P)
                    .build();

            case VERTEX_AI -> VertexAiGeminiChatModel.builder()
                    .project(getGoogleProject())
                    .location(getGoogleLocation())
                    .modelName(modelName)
                    .maxRetries(MAX_RETRIES)
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .temperature((float) TEMPERATURE)
                    .topP((float) TOP_P)
                    .build();
        };
    }

    private static ChatLanguageModel getOpenAiModel(String modelName) {
        return AzureOpenAiChatModel.builder()
                .maxRetries(MAX_RETRIES)
                .apiKey(AgentConfig.getOpenAiApiKey())
                .deploymentName(modelName)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .endpoint(getOpenAiEndpoint())
                .temperature(TEMPERATURE)
                .topP(TOP_P)
                .build();
    }
}
