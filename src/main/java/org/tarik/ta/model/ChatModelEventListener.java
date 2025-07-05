package org.tarik.ta.model;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ChatModelEventListener implements ChatModelListener {
    private static final Logger log = LoggerFactory.getLogger(ChatModelEventListener.class);

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatResponse chatResponse = responseContext.chatResponse();
        AiMessage aiMessage = getAiMessage(chatResponse);
        log.info("AI Message Text: {}", aiMessage.text());
        log.info("AI Message tool execution requests: {}", aiMessage.toolExecutionRequests());
        ChatResponseMetadata metadata = chatResponse.metadata();
        if (metadata != null) {
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
