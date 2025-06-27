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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tarik.ta.helper_entities.TestStep;

import java.util.Objects;

/**
 * Represents the result of a single test step execution.
 *
 * @param testStep The test step that was executed.
 * @param success True if the step executed successfully, false otherwise.
 * @param errorMessage A descriptive error message if the step failed, otherwise null.
 * @param screenshotBase64 A Base64 encoded string of a screenshot taken at the end of the step, can be null.
 */
public record TestStepResult(
        @NotNull TestStep testStep,
        boolean success,
        @Nullable String errorMessage,
        @Nullable String screenshotBase64
) {
    /**
     * Provides a human-friendly string representation of the TestStepResult instance.
     * The output is formatted for console readability.
     *
     * @return A formatted string representing the test step result.
     */
    @Override
    public @NotNull String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TestStepResult:\n");
        sb.append("  - Step: ").append(testStep.toString()).append("\n");
        sb.append("  - Status: ").append(success ? "SUCCESS" : "FAILURE").append("\n");

        if (!success && errorMessage != null && !errorMessage.trim().isEmpty()) {
            sb.append("  - Error: ").append(errorMessage).append("\n");
        }

        boolean screenshotExists = screenshotBase64 != null && !screenshotBase64.trim().isEmpty();
        sb.append("  - Screenshot: ").append(screenshotExists ? "Available" : "Not Available");

        return sb.toString();
    }
}