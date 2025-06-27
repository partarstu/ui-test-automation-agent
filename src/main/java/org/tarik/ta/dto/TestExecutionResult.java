/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tarik.ta.dto;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents the result of the test execution.
 *
 * @param testCaseName The test step that was executed.
 * @param success      True if the all teh test steps were executed successfully, false otherwise.
 * @param stepResults  The execution results of each test step.
 */
public record TestExecutionResult(String testCaseName, boolean success, @NotNull List<TestStepResult> stepResults) {
    @Override
    public @NotNull String toString() {
        StringBuilder sb = new StringBuilder();
        String overallStatus = success ? "PASSED" : "FAILED";

        sb.append("============================================================\n");
        sb.append("Test Case: ").append(testCaseName).append("\n");
        sb.append("Overall Result: ").append(overallStatus).append("\n");
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