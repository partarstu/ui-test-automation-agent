/*
 * Copyright (c) 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.tarik.ta.prompts;

import dev.langchain4j.data.message.Content;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.helper_entities.TestCase;
import java.util.List;
import java.util.Map;
import static com.google.common.base.Preconditions.checkArgument;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class TestCaseExtractionPrompt extends StructuredResponsePrompt<TestCase> {
    private static final String SYSTEM_PROMPT_FILE_NAME = "test_case_extraction_prompt.txt";
    private static final String USER_REQUEST_PLACEHOLDER = "user_request";

    private TestCaseExtractionPrompt(Map<String, String> userMessagePlaceholders) {
        super(Map.of(), userMessagePlaceholders);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected List<Content> getUserMessageAdditionalContents() {
        return List.of();
    }

    @Override
    protected String getUserMessageTemplate() {
        return "{{user_request}}";
    }

    @Override
    protected String getSystemMessageTemplate() {
        return getSystemPromptFileContent(SYSTEM_PROMPT_FILE_NAME);
    }

    @NotNull
    @Override
    public Class<TestCase> getResponseObjectClass() {
        return TestCase.class;
    }

    public static class Builder {
        private String userRequest;

        public Builder withUserRequest(@NotNull String userRequest) {
            this.userRequest = userRequest;
            return this;
        }

        public TestCaseExtractionPrompt build() {
            checkArgument(isNotBlank(userRequest), "User request must be set");
            return new TestCaseExtractionPrompt(Map.of(USER_REQUEST_PLACEHOLDER, userRequest));
        }
    }
}
