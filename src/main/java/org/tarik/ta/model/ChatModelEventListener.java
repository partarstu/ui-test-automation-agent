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
        log.debug("AI Message Text: {}", aiMessage.text());
        log.debug("AI Message tool execution requests: {}", aiMessage.toolExecutionRequests());
        ChatResponseMetadata metadata = chatResponse.metadata();
        if (metadata != null) {
            log.debug("Metadata Model Name: {}", metadata.modelName());
            log.debug("Metadata Finish Reason: {}", metadata.finishReason());
            TokenUsage tokenUsage = metadata.tokenUsage();
            if (tokenUsage != null) {
                log.debug("Input Token Count: {}", tokenUsage.inputTokenCount());
                log.debug("Output Token Count: {}", tokenUsage.outputTokenCount());
                log.debug("Total Token Count: {}", tokenUsage.totalTokenCount());
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
