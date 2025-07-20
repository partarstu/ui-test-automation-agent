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
import org.tarik.ta.helper_entities.TestStep;

import java.awt.image.BufferedImage;
import java.time.Instant;

/**
 * Represents the result of a single test step execution.
 */
public record TestStepResult(@NotNull TestStep testStep,
                             boolean success,
                             @Nullable String errorMessage,
                             @Nullable String actualResult,
                             @Nullable @JsonIgnore BufferedImage screenshot,
                             @Nullable Instant executionStartTimestamp,
                             @Nullable Instant executionEndTimestamp) {
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
        sb.append("  - Step: ").append(testStep).append("\n");
        sb.append("  - Status: ").append(success ? "SUCCESS" : "FAILURE").append("\n");

        if (!success && errorMessage != null && !errorMessage.trim().isEmpty()) {
            sb.append("  - Error: ").append(errorMessage).append("\n");
        }

        boolean screenshotExists = screenshot != null;
        sb.append("  - Screenshot: ").append(screenshotExists ? "Available" : "Not Available").append("\n");
        sb.append("  - Start Time: ").append(executionStartTimestamp != null ? executionStartTimestamp.toString() : "N/A")
                .append("\n");
        sb.append("  - End Time: ").append(executionEndTimestamp != null ? executionEndTimestamp.toString() : "N/A");

        return sb.toString();
    }
}