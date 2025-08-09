package org.tarik.ta.model;

import dev.langchain4j.model.chat.ChatModel;

public class AnthropicChatModel extends GenAiModel {
    public AnthropicChatModel(ChatModel model) {
        super(model);
    }
}
