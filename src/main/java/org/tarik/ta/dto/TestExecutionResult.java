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

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents the result of the test execution.
 */
public final class TestExecutionResult {
    private final String testCaseName;
    private final TestExecutionStatus testExecutionStatus;
    private final @NotNull List<TestStepResult> stepResults;
    private final transient BufferedImage screenshot;
    private final @Nullable Instant executionStartTimestamp;
    private final @Nullable Instant executionEndTimestamp;
    private final @Nullable String generalErrorMessage;

    /**
     * @param testCaseName        The test step that was executed.
     * @param testExecutionStatus Execution status of the test.
     * @param stepResults         The execution results of each test step.
     * @param screenshot          The screenshot of the screen at the time of test execution.
     * @param executionStartTimestamp The timestamp when the test execution started.
     * @param executionEndTimestamp   The timestamp when the test execution ended.
     * @param generalErrorMessage The error message if something happens before any step execution starts, e.g. preconditions fail.
     */
    public TestExecutionResult(
            @NotNull String testCaseName,
            @NotNull TestExecutionStatus testExecutionStatus,
            @NotNull List<TestStepResult> stepResults,
            @Nullable BufferedImage screenshot,
            @Nullable Instant executionStartTimestamp,
            @Nullable Instant executionEndTimestamp,
            @Nullable String generalErrorMessage
    ) {
        this.testCaseName = testCaseName;
        this.testExecutionStatus = testExecutionStatus;
        this.stepResults = stepResults;
        this.screenshot = screenshot;
        this.executionStartTimestamp = executionStartTimestamp;
        this.executionEndTimestamp = executionEndTimestamp;
        this.generalErrorMessage = generalErrorMessage;
    }

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

    public String getTestCaseName() {
        return testCaseName;
    }

    public TestExecutionStatus getTestExecutionStatus() {
        return testExecutionStatus;
    }

    public @NotNull List<TestStepResult> getStepResults() {
        return stepResults;
    }

    public BufferedImage getScreenshot() {
        return screenshot;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (TestExecutionResult) obj;
        return Objects.equals(this.testCaseName, that.testCaseName) &&
                Objects.equals(this.testExecutionStatus, that.testExecutionStatus) &&
                Objects.equals(this.stepResults, that.stepResults) &&
                Objects.equals(this.screenshot, that.screenshot) &&
                Objects.equals(this.executionStartTimestamp, that.executionStartTimestamp) &&
                Objects.equals(this.executionEndTimestamp, that.executionEndTimestamp) &&
                Objects.equals(this.generalErrorMessage, that.generalErrorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testCaseName, testExecutionStatus, stepResults, screenshot, executionStartTimestamp, executionEndTimestamp, generalErrorMessage);
    }

    public @Nullable Instant getExecutionStartTimestamp() {
        return executionStartTimestamp;
    }

    public @Nullable Instant getExecutionEndTimestamp() {
        return executionEndTimestamp;
    }

    public @Nullable String getGeneralErrorMessage() {
        return generalErrorMessage;
    }
}