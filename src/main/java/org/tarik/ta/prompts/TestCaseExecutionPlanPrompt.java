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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.Content;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.dto.TestCaseExecutionPlan;

import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

public class TestCaseExecutionPlanPrompt extends StructuredResponsePrompt<TestCaseExecutionPlan> {
    private static final String SYSTEM_PROMPT_TEMPLATE_FILE = "test_case_execution_plan_prompt.txt";
    private static final String TEST_STEPS_PLACEHOLDER = "test_steps";
    private static final String AVAILABLE_TOOLS_PLACEHOLDER = "available_tools";

    private TestCaseExecutionPlanPrompt(Map<String, String> systemMessagePlaceholders, Map<String, String> userMessagePlaceholders) {
        super(systemMessagePlaceholders, userMessagePlaceholders);
    }

    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    @Override
    public Class<TestCaseExecutionPlan> getResponseObjectClass() {
        return TestCaseExecutionPlan.class;
    }

    @Override
    protected List<Content> getUserMessageAdditionalContents() {
        return List.of();
    }

    @Override
    protected String getUserMessageTemplate() {
        return """
                The provided to you test case steps:
                {{%s}}
                
                All available for interaction tools:
                {{%s}}
                """.formatted(TEST_STEPS_PLACEHOLDER, AVAILABLE_TOOLS_PLACEHOLDER);
    }

    @Override
    protected String getSystemMessageTemplate() {
        return getSystemPromptFileContent(SYSTEM_PROMPT_TEMPLATE_FILE);
    }

    public static class Builder {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
        private List<TestStepInfo> testSteps;
        private List<ToolSpecification> toolSpecifications;

        public Builder withTestSteps(@NotNull List<TestStepInfo> testSteps) {
            this.testSteps = testSteps;
            return this;
        }

        public Builder withToolSpecs(@NotNull List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public TestCaseExecutionPlanPrompt build() {
            checkArgument(!toolSpecifications.isEmpty(), "At least one tool should be provided");
            checkArgument(!testSteps.isEmpty(), "At least one test step should be provided");
            var toolInfos = toolSpecifications.stream()
                    .map(toolSpec->
                            new ToolInfo(toolSpec.name(), toolSpec.description(), toolSpec.parameters().properties().toString()))
                    .toList();
            try {
                Map<String, String> userMessagePlaceholders = Map.of(
                        TEST_STEPS_PLACEHOLDER, OBJECT_MAPPER.writeValueAsString(testSteps),
                        AVAILABLE_TOOLS_PLACEHOLDER, OBJECT_MAPPER.writeValueAsString(toolInfos)
                );
                return new TestCaseExecutionPlanPrompt(Map.of(), userMessagePlaceholders);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Could not convert test steps to JSON", e);
            }
        }

        public record TestStepInfo(String testStepId, String stepDescription, List<String> testData) {
        }

        private record ToolInfo(String toolName, String stepDescription, String parametersDescription) {
        }
    }
}

