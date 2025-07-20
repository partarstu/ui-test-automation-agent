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
package org.tarik.ta.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;

/**
 * Represents the result of the test execution.
 */
public record TestExecutionResult(String testCaseName,
                                  @NotNull TestExecutionStatus testExecutionStatus,
                                  @NotNull List<TestStepResult> stepResults,
                                  @JsonIgnore @Nullable BufferedImage screenshot,
                                  @Nullable Instant executionStartTimestamp,
                                  @Nullable Instant executionEndTimestamp,
                                  @Nullable String generalErrorMessage) {
    public enum TestExecutionStatus {
        PASSED, FAILED, ERROR
    }

    @Override
    public @NotNull String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("============================================================\n");
        sb.append("Test Case: ").append(testCaseName).append("\n");
        sb.append("Execution Result: ").append(testExecutionStatus).append("\n");
        if (generalErrorMessage != null && !generalErrorMessage.isBlank()) {
            sb.append("Error Message: ").append(generalErrorMessage).append("\n");
        }
        sb.append("Start Time: ").append(executionStartTimestamp != null ? executionStartTimestamp.toString() : "N/A").append("\n");
        sb.append("End Time: ").append(executionEndTimestamp != null ? executionEndTimestamp.toString() : "N/A").append("\n");
        sb.append("============================================================\n");
        sb.append("Steps:\n");

        if (stepResults.isEmpty()) {
            sb.append("  - No steps were executed.\n");
        } else {
            for (int i = 0; i < stepResults.size(); i++) {
                TestStepResult result = stepResults.get(i);
                sb.append("\n[Step ").append(i + 1).append("]\n");
                // Indent the output from the TestStepResult.toString() for better hierarchy
                String indentedStepResult = "  " + result.toString().replaceAll("\n", "\n  ");
                sb.append(indentedStepResult).append("\n");
            }
        }

        sb.append("====================== End of Test =======================");

        return sb.toString();
    }
}