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

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Represents the result of a single test step execution.
 */
public final class TestStepResult {
    private final @NotNull TestStep testStep;
    private final boolean success;
    private final @Nullable String errorMessage;
    private final @Nullable String actualResult;
    private final @Nullable transient BufferedImage screenshot;

    /**
     * @param testStep     The test step that was executed.
     * @param success      True if the step executed successfully, false otherwise.
     * @param errorMessage A descriptive error message if the step failed, otherwise null.
     * @param screenshot   A screenshot taken at the end of the step, can be null.
     */
    public TestStepResult(
            @NotNull TestStep testStep,
            boolean success,
            @Nullable String errorMessage,
            @Nullable String actualResult,
            @Nullable BufferedImage screenshot
    ) {
        this.testStep = testStep;
        this.success = success;
        this.errorMessage = errorMessage;
        this.actualResult = actualResult;
        this.screenshot = screenshot;
    }

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
        sb.append("  - Screenshot: ").append(screenshotExists ? "Available" : "Not Available");

        return sb.toString();
    }

    public @NotNull TestStep getTestStep() {
        return testStep;
    }

    public boolean isSuccessful() {
        return success;
    }

    public @Nullable String getErrorMessage() {
        return errorMessage;
    }

    public @Nullable String getActualResult() {
        return actualResult;
    }

    public @Nullable BufferedImage getScreenshot() {
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
        var that = (TestStepResult) obj;
        return Objects.equals(this.testStep, that.testStep) &&
                this.success == that.success &&
                Objects.equals(this.errorMessage, that.errorMessage) &&
                Objects.equals(this.actualResult, that.actualResult) &&
                Objects.equals(this.screenshot, that.screenshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(testStep, success, errorMessage, actualResult, screenshot);
    }

}