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

import dev.langchain4j.data.message.Content;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.dto.PageDescriptionResult;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class PageDescriptionPrompt extends StructuredResponsePrompt<PageDescriptionResult> {
    private static final String SYSTEM_PROMPT_FILE_NAME = "page_description_prompt.txt";
    private final BufferedImage screenshot;

    private PageDescriptionPrompt(@NotNull Map<String, String> systemMessagePlaceholders,
                                  @NotNull Map<String, String> userMessagePlaceholders,
                                  @NotNull BufferedImage screenshot) {
        super(systemMessagePlaceholders, userMessagePlaceholders);
        this.screenshot = screenshot;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected String getUserMessageTemplate() {
        return """                                
                Here is the screenshot:
                """;
    }

    @Override
    protected List<Content> getUserMessageAdditionalContents() {
        return List.of(singleImageContent(screenshot));
    }

    @Override
    protected String getSystemMessageTemplate() {
        return getSystemPromptFileContent(SYSTEM_PROMPT_FILE_NAME);
    }

    @NotNull
    @Override
    public Class<PageDescriptionResult> getResponseObjectClass() {
        return PageDescriptionResult.class;
    }

    public static class Builder {
        private BufferedImage screenshot;

        public Builder withScreenshot(@NotNull BufferedImage screenshot) {
            this.screenshot = requireNonNull(screenshot, "Screenshot cannot be null");
            return this;
        }

        public PageDescriptionPrompt build() {
            return new PageDescriptionPrompt(Map.of(), Map.of(), screenshot);
        }
    }
}
