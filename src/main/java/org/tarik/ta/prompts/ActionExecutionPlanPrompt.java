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
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.dto.ExecutionPlan;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Optional.ofNullable;

public class ActionExecutionPlanPrompt extends StructuredResponsePrompt<ExecutionPlan> {
    private static final String SYSTEM_PROMPT_TEMPLATE_FILE = "action_execution_plan_prompt.txt";
    private static final String ACTIONS_PLACEHOLDER = "actions";
    private static final String AVAILABLE_TOOLS_PLACEHOLDER = "available_tools";

    private ActionExecutionPlanPrompt(Map<String, String> systemMessagePlaceholders, Map<String, String> userMessagePlaceholders) {
        super(systemMessagePlaceholders, userMessagePlaceholders);
    }

    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    @Override
    public Class<ExecutionPlan> getResponseObjectClass() {
        return ExecutionPlan.class;
    }

    @Override
    protected List<Content> getUserMessageAdditionalContents() {
        return List.of();
    }

    @Override
    protected String getUserMessageTemplate() {
        return """
                The provided to you actions:
                {{%s}}
                
                All available for interaction tools:
                {{%s}}
                """.formatted(ACTIONS_PLACEHOLDER, AVAILABLE_TOOLS_PLACEHOLDER);
    }

    @Override
    protected String getSystemMessageTemplate() {
        return getSystemPromptFileContent(SYSTEM_PROMPT_TEMPLATE_FILE);
    }

    public static class Builder {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
        private List<ActionInfo> actions;
        private List<ToolSpecification> toolSpecifications;

        public Builder withActions(@NotNull List<ActionInfo> actions) {
            this.actions = actions;
            return this;
        }

        public Builder withToolSpecs(@NotNull List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public ActionExecutionPlanPrompt build() {
            checkArgument(!toolSpecifications.isEmpty(), "At least one tool should be provided");
            checkArgument(!actions.isEmpty(), "At least one action should be provided");
            var toolInfos = toolSpecifications.stream()
                    .filter(Objects::nonNull)
                    .map(toolSpec -> new ToolInfo(toolSpec.name(), toolSpec.description(),
                            ofNullable(toolSpec.parameters()).map(JsonObjectSchema::properties).map(Object::toString).orElse("")))
                    .toList();
            try {
                Map<String, String> userMessagePlaceholders = Map.of(
                        ACTIONS_PLACEHOLDER, OBJECT_MAPPER.writeValueAsString(actions),
                        AVAILABLE_TOOLS_PLACEHOLDER, OBJECT_MAPPER.writeValueAsString(toolInfos)
                );
                return new ActionExecutionPlanPrompt(Map.of(), userMessagePlaceholders);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Could not convert actions to JSON", e);
            }
        }

        public record ActionInfo(String actionId, String actionDescription, List<String> inputData) {
        }

        private record ToolInfo(String toolName, String toolDescription, String parametersDescription) {
        }
    }
}
