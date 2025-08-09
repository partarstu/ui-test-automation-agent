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
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;

import java.util.List;

import static java.util.Collections.singletonList;

import static org.tarik.ta.AgentConfig.*;

public class ModelFactory {
    private static final String INSTRUCTION_MODEL_NAME = getInstructionModelName();
    private static final String VISION_MODEL_NAME = getVisionModelName();
    private static final int MAX_RETRIES = getMaxRetries();
    private static final int MAX_OUTPUT_TOKENS = getMaxOutputTokens();
    private static final double TEMPERATURE = getTemperature();
    private static final double TOP_P = getTopP();
    private static final ModelProvider INSTRUCTION_MODEL_PROVIDER = getInstructionModelProvider();
    private static final ModelProvider VISION_MODEL_PROVIDER = getVisionModelProvider();
    private static final boolean LOG_MODEL_OUTPUTS = isModelLoggingEnabled();
    private static final boolean OUTPUT_THOUGHTS = isThinkingOutputEnabled();
    private static final int GEMINI_THINKING_BUDGET = getGeminiThinkingBudget();

    public static GenAiModel getInstructionModel() {
        return switch (INSTRUCTION_MODEL_PROVIDER) {
            case GOOGLE -> new GenAiModel(getGeminiModel(INSTRUCTION_MODEL_NAME, LOG_MODEL_OUTPUTS, OUTPUT_THOUGHTS));
            case OPENAI -> new GenAiModel(getOpenAiModel(INSTRUCTION_MODEL_NAME));
            case GROQ -> new GenAiModel(getGroqModel(INSTRUCTION_MODEL_NAME));
            case ANTHROPIC -> new GenAiModel(getAnthropicModel(INSTRUCTION_MODEL_NAME));
        };
    }

    public static GenAiModel getVisionModel() {
        return switch (VISION_MODEL_PROVIDER) {
            case GOOGLE -> new GenAiModel(getGeminiModel(VISION_MODEL_NAME, LOG_MODEL_OUTPUTS, OUTPUT_THOUGHTS));
            case OPENAI -> new GenAiModel(getOpenAiModel(VISION_MODEL_NAME));
            case GROQ -> new GenAiModel(getGroqModel(VISION_MODEL_NAME));
            case ANTHROPIC -> new GenAiModel(getAnthropicModel(VISION_MODEL_NAME));
        };
    }

    private static ChatModel getGeminiModel(String modelName, boolean logResponses, boolean outputThoughts) {
        var provider = getGoogleApiProvider();
        return switch (provider) {
            case STUDIO_AI -> GoogleAiGeminiChatModel.builder()
                    .apiKey(getGoogleApiToken())
                    .modelName(modelName)
                    .maxRetries(MAX_RETRIES)
                    .maxOutputTokens(MAX_OUTPUT_TOKENS)
                    .temperature(TEMPERATURE)
                    .topP(TOP_P)
                    .logRequestsAndResponses(logResponses)
                    .thinkingConfig(GeminiThinkingConfig.builder()
                            .includeThoughts(outputThoughts)
                            .thinkingBudget(GEMINI_THINKING_BUDGET)
                            .build())
                    .listeners(List.of(new ChatModelEventListener()))
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
                    .listeners(List.of(new ChatModelEventListener()))
                    .build();
        };
    }

    private static ChatModel getOpenAiModel(String modelName) {
        return AzureOpenAiChatModel.builder()
                .maxRetries(MAX_RETRIES)
                .apiKey(getOpenAiApiKey())
                .deploymentName(modelName)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .endpoint(getOpenAiEndpoint())
                .temperature(TEMPERATURE)
                .topP(TOP_P)
                .listeners(singletonList(new ChatModelEventListener()))
                .build();
    }

    private static ChatModel getGroqModel(String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl(getGroqEndpoint())
                .modelName(modelName)
                .maxRetries(MAX_RETRIES)
                .apiKey(getGroqApiKey())
                .maxTokens(MAX_OUTPUT_TOKENS)
                .temperature(TEMPERATURE)
                .topP(TOP_P)
                .listeners(singletonList(new ChatModelEventListener()))
                .build();
    }

    private static ChatModel getAnthropicModel(String modelName) {
        return AnthropicChatModel.builder()
                .baseUrl(getAnthropicEndpoint())
                .apiKey(getAnthropicApiKey())
                .modelName(modelName)
                .maxRetries(MAX_RETRIES)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .temperature(TEMPERATURE)
                .topP(TOP_P)
                .listeners(singletonList(new ChatModelEventListener()))
                .build();
    }
}
