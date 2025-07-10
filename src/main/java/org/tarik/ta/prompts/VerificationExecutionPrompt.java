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
import org.tarik.ta.dto.VerificationExecutionResult;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class VerificationExecutionPrompt extends StructuredResponsePrompt<VerificationExecutionResult> {
    private static final String SYSTEM_PROMPT_FILE_NAME = "verification_execution_prompt.txt";
    private static final String VERIFICATION_DESCRIPTION_PLACEHOLDER = "verification_description";
    private static final String ACTION_DESCRIPTION_PLACEHOLDER = "action_description";
    private final BufferedImage screenshot;

    private VerificationExecutionPrompt(@NotNull Map<String, String> systemMessagePlaceholders,
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
        return ("""
                Verify that {{%s}}.
                
                The test case action executed before this verification: {{%s}}.
                
                The screenshot of the application under test:
                """).formatted(VERIFICATION_DESCRIPTION_PLACEHOLDER, ACTION_DESCRIPTION_PLACEHOLDER);
    }

    @Override
    protected String getSystemMessageTemplate() {
        return getSystemPromptFileContent(SYSTEM_PROMPT_FILE_NAME);
    }

    @NotNull
    @Override
    public Class<VerificationExecutionResult> getResponseObjectClass() {
        return VerificationExecutionResult.class;
    }

    public static class Builder {
        private String verificationDescription;
        private String actionDescription;
        private BufferedImage screenshot;

        public Builder withVerificationDescription(@NotNull String verificationDescription) {
            this.verificationDescription = verificationDescription;
            return this;
        }

        public Builder withActionDescription(@NotNull String actionDescription) {
            this.actionDescription = actionDescription;
            return this;
        }

        public Builder screenshot(@NotNull BufferedImage screenshot) {
            this.screenshot = screenshot;
            return this;
        }

        public VerificationExecutionPrompt build() {
            checkArgument(isNotBlank(verificationDescription), "Verification description must be set");
            checkArgument(isNotBlank(actionDescription), "Action description must be set");
            return new VerificationExecutionPrompt(
                    Map.of(),
                    Map.of(
                            VERIFICATION_DESCRIPTION_PLACEHOLDER, verificationDescription,
                            ACTION_DESCRIPTION_PLACEHOLDER, actionDescription),
                    screenshot);
        }
    }
}