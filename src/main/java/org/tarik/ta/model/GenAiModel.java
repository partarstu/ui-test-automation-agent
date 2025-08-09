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

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.prompts.StructuredResponsePrompt;

import java.time.Instant;
import java.util.List;

import static java.time.Duration.between;
import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;
import static org.tarik.ta.utils.ModelUtils.parseModelResponseAsObject;

public class GenAiModel implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(GenAiModel.class);
    private final ChatModel chatLanguageModel;

    public GenAiModel(@NotNull ChatModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public <T> T generateAndGetResponseAsObject(@NotNull StructuredResponsePrompt<T> prompt, @NotNull String generationDescription) {
        Class<T> objectClass = prompt.getResponseObjectClass();

        ChatResponse response;
        if (chatLanguageModel instanceof AnthropicChatModel) {
            response = generate(prompt.getSystemMessage(), prompt.getUserMessage(), generationDescription);
            return parseModelResponseAsObject(response, objectClass, true);
        } else {
            response = generate(prompt.getSystemMessage(), prompt.getUserMessage(), generationDescription, ResponseFormatType.JSON);
            return parseModelResponseAsObject(response, objectClass, false);
        }

    }

    @Override
    public void close() {
        if (chatLanguageModel instanceof AutoCloseable closeableModel) {
            try {
                closeableModel.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private ChatResponse generate(SystemMessage systemMessage, UserMessage userMessage,
                                  String generationDescription, @Nullable ResponseFormatType responseFormatType) {
        var start = now();
        var chatRequestBuilder = ChatRequest.builder().messages(systemMessage, userMessage);
        if (responseFormatType != null) {
            chatRequestBuilder.responseFormat(ResponseFormat.builder().type(responseFormatType).build());
        }
        var response = chatLanguageModel.chat(chatRequestBuilder.build());
        validateAndLogResponse(generationDescription, response, start);
        return response;
    }

    private ChatResponse generate(SystemMessage systemMessage, UserMessage userMessage,
                                  String generationDescription) {
        return generate(systemMessage, userMessage, generationDescription, null);
    }

    private ChatResponse generate(SystemMessage systemMessage, UserMessage userMessage,
                                  List<ToolSpecification> toolSpecifications, String generationDescription,
                                  ResponseFormatType responseFormatType) {
        var start = now();
        var paramsBuilder = ChatRequestParameters.builder().toolSpecifications(toolSpecifications);
        var chatRequest = ChatRequest.builder()
                .messages(systemMessage, userMessage)
                .responseFormat(ResponseFormat.builder().type(responseFormatType).build())
                .parameters(paramsBuilder.build())
                .build();
        var response = chatLanguageModel.chat(chatRequest);
        validateAndLogResponse(generationDescription, response, start);
        return response;
    }

    private void validateAndLogResponse(String generationDescription, ChatResponse response, Instant start) {
        var responseMetadata = response.metadata();
        var message = response.aiMessage();
        requireNonNull(response, "Model response can't be null");
        requireNonNull(responseMetadata, "Model response metadata can't be null");
        requireNonNull(message, "Model response message can't be null");
        LOG.debug("Done content generation for {} by {} in {} millis",
                generationDescription, responseMetadata.modelName(), between(start, now()).toMillis());
    }
}
