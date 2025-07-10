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
import org.tarik.ta.dto.BoundingBox;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class ElementBoundingBoxPrompt extends StructuredResponsePrompt<BoundingBox> {
    private static final String SYSTEM_PROMPT_FILE_NAME = "element_bounding_box_prompt.txt";
    private static final String ELEMENT_DESCRIPTION_PLACEHOLDER = "target_element_description";
    private final BufferedImage screenshot;

    private ElementBoundingBoxPrompt(@NotNull Map<String, String> systemMessagePlaceholders,
                                     @NotNull Map<String, String> userMessagePlaceholders,
                                     @NotNull BufferedImage screenshot) {
        super(systemMessagePlaceholders, userMessagePlaceholders);
        this.screenshot = screenshot;
    }

    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    @Override
    public Class<BoundingBox> getResponseObjectClass() {
        return BoundingBox.class;
    }

    @Override
    protected List<Content> getUserMessageAdditionalContents() {
        return List.of(singleImageContent(screenshot));
    }

    @Override
    protected String getUserMessageTemplate() {
        return ("Here is the screenshot:\n");
    }

    @Override
    protected String getSystemMessageTemplate() {
        return getSystemPromptFileContent(SYSTEM_PROMPT_FILE_NAME);
    }

    public static class Builder {
        private String elementDescription;
        private BufferedImage screenshot;

        public Builder withElementDescription(@NotNull String elementDescription) {
            this.elementDescription = elementDescription;
            return this;
        }

        public Builder withScreenshot(@NotNull BufferedImage screenshot) {
            this.screenshot = screenshot;
            return this;
        }

        public ElementBoundingBoxPrompt build() {
            Map<String, String> systemMessagePlaceholders = Map.of(ELEMENT_DESCRIPTION_PLACEHOLDER, elementDescription);
            checkArgument(isNotBlank(elementDescription), "Element description must be set");
            return new ElementBoundingBoxPrompt(systemMessagePlaceholders, Map.of(), screenshot);
        }
    }
}
