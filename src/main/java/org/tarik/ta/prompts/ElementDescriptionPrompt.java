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
import org.tarik.ta.dto.UiElementDescriptionResult;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static java.awt.Color.GREEN;
import static java.util.Objects.requireNonNull;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class ElementDescriptionPrompt extends StructuredResponsePrompt<UiElementDescriptionResult> {
    private static final String SYSTEM_PROMPT_FILE_NAME = "element_description_prompt.txt";
    private static final String USER_PROMPT_TEXT = "The provided to you screenshot:\n";
    private static final String ORIGINAL_ELEMENT_DESCRIPTION_PLACEHOLDER = "original_element_description";
    private static final String BOUNDING_BOX_COLOR_PLACEHOLDER = "bounding_box_color";

    private final BufferedImage screenshot;

    private ElementDescriptionPrompt(@NotNull Map<String, String> systemMessagePlaceholders,
                                     @NotNull Map<String, String> userMessagePlaceholders,
                                     @NotNull BufferedImage screenshot) {
        super(systemMessagePlaceholders, userMessagePlaceholders);
        this.screenshot = screenshot;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected List<Content> getUserMessageAdditionalContents() {
        return List.of(singleImageContent(screenshot));
    }

    @Override
    protected String getUserMessageTemplate() {
        return USER_PROMPT_TEXT;
    }

    @Override
    protected String getSystemMessageTemplate() {
        return getSystemPromptFileContent(SYSTEM_PROMPT_FILE_NAME);
    }

    @NotNull
    @Override
    public Class<UiElementDescriptionResult> getResponseObjectClass() {
        return UiElementDescriptionResult.class;
    }


    public static class Builder {
        private String originalElementDescription;
        private BufferedImage screenshot;
        private Color boundingBoxColor = GREEN;

        public Builder withOriginalElementDescription(@NotNull String description) {
            this.originalElementDescription = description;
            return this;
        }

        public Builder withBoundingBoxColor(@NotNull Color boundingBoxColor) {
            this.boundingBoxColor = boundingBoxColor;
            return this;
        }

        public Builder withScreenshot(@NotNull BufferedImage screenshot) {
            this.screenshot = requireNonNull(screenshot, "Screenshot cannot be null");
            return this;
        }

        public ElementDescriptionPrompt build() {
            checkArgument(isNotBlank(originalElementDescription), "Original element description must be set");
            Map<String, String> systemPlaceholders = Map.of(
                    ORIGINAL_ELEMENT_DESCRIPTION_PLACEHOLDER, originalElementDescription,
                    BOUNDING_BOX_COLOR_PLACEHOLDER, boundingBoxColor.toString().toLowerCase()
            );
            return new ElementDescriptionPrompt(systemPlaceholders, Map.of(), screenshot);
        }
    }
}