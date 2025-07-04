package org.tarik.ta.model;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public class ChatModelEventListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(ChatModelEventListener.class);

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
       /* ChatRequest chatRequest = requestContext.chatRequest();

        List<ChatMessage> messages = chatRequest.messages();
        String userMessage = getUserMessageContentsDescription(messages);

        log.info("Request System message: {}",
                messages.stream().filter(m->m instanceof SystemMessage).findFirst().orElse(null));
        log.info("Request User message:\n{}", userMessage);

        ChatRequestParameters parameters = chatRequest.parameters();
        if (parameters != null) {
            log.info("Model Name: {}", parameters.modelName());
            log.info("Temperature: {}", parameters.temperature());
            log.info("TopP: {}", parameters.topP());
            log.info("Max Output Tokens: {}", parameters.maxOutputTokens());
            log.info("Tool Specifications: {}", parameters.toolSpecifications());
            log.info("Tool Choice: {}", parameters.toolChoice());
            log.info("Response Format: {}", parameters.responseFormat());
        }
        log.info("Model Provider: {}", requestContext.modelProvider());*/
    }

    @NotNull
    private static String getUserMessageContentsDescription(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).contents())
                .flatMap(Collection::stream)
                .map(content -> {
                    if (content instanceof TextContent) {
                        return ((TextContent) content).text();
                    }
                    return content.type().name() + "_COUNT:1"; // Represent non-text content with its type and count
                })
                .collect(joining("\n"));
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatResponse chatResponse = responseContext.chatResponse();
        AiMessage aiMessage = getAiMessage(chatResponse);
        log.info("AI Message Text: {}", aiMessage.text());
        log.info("AI Message tool execution requests: {}", aiMessage.toolExecutionRequests());
        ChatResponseMetadata metadata = chatResponse.metadata();
        if (metadata != null) {
            log.info("Metadata ID: {}", metadata.id());
            log.info("Metadata Model Name: {}", metadata.modelName());
            log.info("Metadata Finish Reason: {}", metadata.finishReason());
            TokenUsage tokenUsage = metadata.tokenUsage();
            if (tokenUsage != null) {
                log.info("Input Token Count: {}", tokenUsage.inputTokenCount());
                log.info("Output Token Count: {}", tokenUsage.outputTokenCount());
                log.info("Total Token Count: {}", tokenUsage.totalTokenCount());
            }
        }
    }

    private static AiMessage getAiMessage(ChatResponse chatResponse) {
        return chatResponse.aiMessage();
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Throwable error = errorContext.error();
        log.error("Error: ", error);
        Map<Object, Object> attributes = errorContext.attributes();
        log.info("Attributes on Error: {}", attributes.get("my-attribute"));
    }
}
