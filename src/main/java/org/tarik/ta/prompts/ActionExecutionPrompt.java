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

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class ActionExecutionPrompt extends AbstractPrompt {
    private static final String SYSTEM_PROMPT_FILE_NAME = "action_execution_prompt.txt";
    private static final String ACTION_DESCRIPTION_PLACEHOLDER = "action_description";
    private final BufferedImage screenshot;

    private ActionExecutionPrompt(@NotNull Map<String, String> systemMessagePlaceholders,
                                  @NotNull Map<String, String> userMessagePlaceholders, BufferedImage screenshot) {
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
        return "The action is: {{%s}}".formatted(ACTION_DESCRIPTION_PLACEHOLDER);
    }

    @Override
    protected String getSystemMessageTemplate() {
        return getSystemPromptFileContent(SYSTEM_PROMPT_FILE_NAME);
    }

    public static class Builder {
        private String actionDescription;
        private BufferedImage screenshot;

        public Builder withActionDescription(@NotNull String action) {
            this.actionDescription = action;
            return this;
        }

        public Builder screenshot(BufferedImage screenshot) {
            this.screenshot = screenshot;
            return this;
        }

        public ActionExecutionPrompt build() {
            checkArgument(isNotBlank(actionDescription), "Action description must be set");
            return new ActionExecutionPrompt(Map.of(), Map.of(ACTION_DESCRIPTION_PLACEHOLDER, actionDescription), screenshot);
        }
    }
}