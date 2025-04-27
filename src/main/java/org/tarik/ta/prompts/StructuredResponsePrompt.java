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
package org.tarik.ta.prompts;

import dev.langchain4j.data.message.SystemMessage;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static dev.langchain4j.data.message.SystemMessage.from;
import static org.tarik.ta.utils.ModelUtils.extendPromptWithResponseObjectInfo;

/**
 * Abstract class for prompts that expect a structured response object of a specific type.
 *
 * @param <T> The type of the expected response object.
 */
public abstract class StructuredResponsePrompt<T> extends AbstractPrompt {

    protected StructuredResponsePrompt(@NotNull Map<String, String> systemMessagePlaceholders,
                                       @NotNull Map<String, String> userMessagePlaceholders) {
        super(systemMessagePlaceholders, userMessagePlaceholders);
    }

    @Override
    public SystemMessage getSystemMessage() {
        var message = super.getSystemMessage();
        return from(extendPromptWithResponseObjectInfo(message.text(), getResponseObjectClass()));
    }

    /**
     * Returns the class of the expected response object.
     *
     * @return The Class object representing the type T.
     */
    @NotNull
    public abstract Class<T> getResponseObjectClass();
}