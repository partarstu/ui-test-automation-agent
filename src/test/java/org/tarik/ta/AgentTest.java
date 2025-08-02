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

package org.tarik.ta;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.dto.*;
import org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.helper_entities.TestCase;
import org.tarik.ta.helper_entities.TestStep;
import org.tarik.ta.model.GenAiModel;
import org.tarik.ta.model.ModelFactory;
import org.tarik.ta.prompts.ActionExecutionPlanPrompt;
import org.tarik.ta.prompts.VerificationExecutionPrompt;
import org.tarik.ta.tools.AbstractTools.ToolExecutionResult;
import org.tarik.ta.tools.CommonTools;
import org.tarik.ta.utils.CommonUtils;
import org.tarik.ta.utils.ImageUtils;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.Optional.*;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus.FAILED;
import static org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus.PASSED;
import static org.tarik.ta.model.ModelFactory.getInstructionModel;
import static org.tarik.ta.model.ModelFactory.getVisionModel;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.ERROR;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.SUCCESS;
import static org.tarik.ta.tools.CommonTools.waitSeconds;
import static org.tarik.ta.utils.CommonUtils.getObjectPrettyPrinted;
import static org.tarik.ta.utils.CommonUtils.sleepMillis;

@ExtendWith(MockitoExtension.class)
class AgentTest {
    private static final String ACTION_EXECUTION_PLAN_GENERATION = "action execution plan generation";
    private static final UUID UUID_1 = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcde1");
    private static final UUID UUID_2 = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcde2");
    @Mock
    private GenAiModel mockModel;
    @Mock
    private BufferedImage mockScreenshot;

    // Static mocks
    private MockedStatic<ModelFactory> modelFactoryMockedStatic;
    private MockedStatic<CommonUtils> commonUtilsMockedStatic;
    private MockedStatic<AgentConfig> agentConfigMockedStatic;
    private MockedStatic<CommonTools> commonToolsMockedStatic;
    private MockedStatic<ImageUtils> imageUtilsMockedStatic;
    private MockedStatic<UUID> uuidMockedStatic;


    // Constants for configuration
    private static final int TEST_STEP_TIMEOUT_MILLIS = 50;
    private static final int VERIFICATION_TIMEOUT_MILLIS = 50;
    private static final int RETRY_INTERVAL_MILLIS = 10;
    private static final int VERIFICATION_DELAY_MILLIS = 5;
    private static final int TOOL_PARAM_WAIT_AMOUNT_SECONDS = 1;
    private static final String MOCK_TOOL_NAME = "waitSeconds";
    private static final List<String> MOCK_TOOL_ARGS_LIST = List.of("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS);
    private static final UUID MOCK_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef");


    @BeforeEach
    void setUp() {
        modelFactoryMockedStatic = mockStatic(ModelFactory.class);
        commonUtilsMockedStatic = mockStatic(CommonUtils.class);
        agentConfigMockedStatic = mockStatic(AgentConfig.class);
        commonToolsMockedStatic = mockStatic(CommonTools.class);
        imageUtilsMockedStatic = mockStatic(ImageUtils.class);
        uuidMockedStatic = mockStatic(UUID.class);

        // Model Factory
        modelFactoryMockedStatic.when(() -> getInstructionModel(anyBoolean())).thenReturn(mockModel);
        modelFactoryMockedStatic.when(() -> getVisionModel(anyBoolean())).thenReturn(mockModel);

        // Common Utils & ImageUtils
        commonUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(anyString())).thenCallRealMethod();
        commonUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(isNull())).thenReturn(false);
        commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mockScreenshot);
        commonUtilsMockedStatic.when(() -> sleepMillis(anyInt())).thenAnswer(_ -> null); // No actual sleep
        commonUtilsMockedStatic.when(() -> CommonUtils.waitUntil(any(Instant.class))).thenAnswer(_ -> null);
        commonToolsMockedStatic.when(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS)))
                .thenReturn(new ToolExecutionResult(SUCCESS, "Wait completed", false));
        imageUtilsMockedStatic.when(() -> ImageUtils.convertImageToBase64(any(), anyString())).thenReturn("mock-base64-string");

        // Agent Config
        agentConfigMockedStatic.when(AgentConfig::getTestStepExecutionRetryTimeoutMillis).thenReturn(TEST_STEP_TIMEOUT_MILLIS);
        agentConfigMockedStatic.when(AgentConfig::getVerificationRetryTimeoutMillis).thenReturn(VERIFICATION_TIMEOUT_MILLIS);
        agentConfigMockedStatic.when(AgentConfig::getTestStepExecutionRetryIntervalMillis).thenReturn(RETRY_INTERVAL_MILLIS);
        agentConfigMockedStatic.when(AgentConfig::getActionVerificationDelayMillis).thenReturn(VERIFICATION_DELAY_MILLIS);

        lenient().when(mockModel.generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class),
                        eq("verification execution")))
                .thenReturn(new VerificationExecutionResult(true, "Verification successful"));

        lenient().when(UUID.randomUUID()).thenReturn(MOCK_UUID);
        var toolExecutionRequest = new ActionExecutionPlan(MOCK_UUID.toString(), MOCK_TOOL_NAME, MOCK_TOOL_ARGS_LIST);
        var testCaseExecutionPlan = new ExecutionPlan(List.of(toolExecutionRequest));
        lenient().when(mockModel.generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), anyString()))
                .thenReturn(testCaseExecutionPlan);
    }

    @AfterEach
    void tearDown() {
        // Close static mocks
        modelFactoryMockedStatic.close();
        commonUtilsMockedStatic.close();
        agentConfigMockedStatic.close();
        commonToolsMockedStatic.close();
        imageUtilsMockedStatic.close();
        uuidMockedStatic.close();
    }

    @Test
    @DisplayName("Single test step with action and successful verification")
    void singleStepActionAndVerificationSuccess() {
        // Given
        TestStep step = new TestStep("Perform Action", null, "Verify Result");
        TestCase testCase = new TestCase("Single Step Success", null, List.of(step));

        // When
        TestExecutionResult result = Agent.executeTestCase(testCase);

        // Then
        assertThat(result.testExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.executionStartTimestamp()).isNotNull();
        assertThat(result.executionEndTimestamp()).isNotNull();
        assertThat(result.stepResults()).hasSize(1);
        assertThat(result.stepResults().getFirst().success()).isTrue();
        assertThat(result.stepResults().getFirst().executionStartTimestamp()).isNotNull();
        assertThat(result.stepResults().getFirst().executionEndTimestamp()).isNotNull();

        verify(mockModel).generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), eq(ACTION_EXECUTION_PLAN_GENERATION));
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS)));
        commonUtilsMockedStatic.verify(() -> sleepMillis(VERIFICATION_DELAY_MILLIS));
        commonUtilsMockedStatic.verify(CommonUtils::captureScreen, times(1));
        verify(mockModel).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), eq("verification execution"));
    }

    @Test
    @DisplayName("Single step with action only (no verification)")
    void singleStepActionOnlySuccess() {
        // Given
        TestStep step = new TestStep("Perform Action Only", null, null);
        TestCase testCase = new TestCase("Single Action Only", null, List.of(step));

        // When
        TestExecutionResult result = Agent.executeTestCase(testCase);

        // Then
        assertThat(result.testExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.executionStartTimestamp()).isNotNull();
        assertThat(result.executionEndTimestamp()).isNotNull();
        assertThat(result.stepResults()).hasSize(1);
        assertThat(result.stepResults().getFirst().success()).isTrue();
        assertThat(result.stepResults().getFirst().executionStartTimestamp()).isNotNull();
        assertThat(result.stepResults().getFirst().executionEndTimestamp()).isNotNull();

        verify(mockModel).generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), eq(ACTION_EXECUTION_PLAN_GENERATION));
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS)));
        commonUtilsMockedStatic.verify(() -> sleepMillis(anyInt()), never());
        commonUtilsMockedStatic.verify(CommonUtils::captureScreen, never());
        verify(mockModel, never()).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), anyString());
    }

    @Test
    @DisplayName("Multiple steps with actions and successful verifications including test data")
    void multipleStepsIncludingTestDataSuccess() {
        // Given
        when(UUID.randomUUID()).thenReturn(UUID_1, UUID_2, MOCK_UUID);

        TestStep step1 = new TestStep("Action 1", null, "Verify 1");
        TestStep step2 = new TestStep("Action 2", List.of("data"), "Verify 2"); // With test data
        TestCase testCase = new TestCase("Multi-Step Success", null, List.of(step1, step2));

        var plan1 = new ActionExecutionPlan(UUID_1.toString(), MOCK_TOOL_NAME, MOCK_TOOL_ARGS_LIST);
        var plan2 = new ActionExecutionPlan(UUID_2.toString(), MOCK_TOOL_NAME, MOCK_TOOL_ARGS_LIST);
        var testCaseExecutionPlan = new ExecutionPlan(List.of(plan1, plan2));
        when(mockModel.generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), anyString()))
                .thenReturn(testCaseExecutionPlan);

        commonToolsMockedStatic.when(() -> waitSeconds(eq("1")))
                .thenReturn(new ToolExecutionResult(SUCCESS, "Wait 1 OK", false))
                .thenReturn(new ToolExecutionResult(SUCCESS, "Wait 2 OK", false));
        when(mockModel.generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), eq("verification execution")))
                .thenReturn(new VerificationExecutionResult(true, "Verify 1 OK"))
                .thenReturn(new VerificationExecutionResult(true, "Verify 2 OK"));

        // When
        TestExecutionResult result = Agent.executeTestCase(testCase);

        // Then
        assertThat(result.testExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.executionStartTimestamp()).isNotNull();
        assertThat(result.executionEndTimestamp()).isNotNull();
        assertThat(result.stepResults()).hasSize(2);
        assertThat(result.stepResults()).allMatch(TestStepResult::success);
        assertThat(result.stepResults()).allMatch(stepResult -> stepResult.executionStartTimestamp() != null);
        assertThat(result.stepResults()).allMatch(stepResult -> stepResult.executionEndTimestamp() != null);

        verify(mockModel, times(1)).generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class),
                eq(ACTION_EXECUTION_PLAN_GENERATION));
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("1")), times(2));
        commonUtilsMockedStatic.verify(() -> sleepMillis(VERIFICATION_DELAY_MILLIS), times(2));
        commonUtilsMockedStatic.verify(CommonUtils::captureScreen, times(2));
        verify(mockModel, times(2)).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class),
                eq("verification execution"));
    }

    @Test
    @DisplayName("Single step with action using test data")
    void singleStepWithDataSuccess() {
        // Given
        List<String> testData = List.of("input1", "input2");
        TestStep step = new TestStep("Action With Data", testData, "Verify Data Action");
        TestCase testCase = new TestCase("Action Data Success", null, List.of(step));
        ArgumentCaptor<ActionExecutionPlanPrompt> promptCaptor = forClass(ActionExecutionPlanPrompt.class);

        // When
        TestExecutionResult result = Agent.executeTestCase(testCase);

        // Then
        assertThat(result.testExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.executionStartTimestamp()).isNotNull();
        assertThat(result.executionEndTimestamp()).isNotNull();
        verify(mockModel).generateAndGetResponseAsObject(promptCaptor.capture(), eq(ACTION_EXECUTION_PLAN_GENERATION));
        assertThat(promptCaptor.getValue().getUserMessage().toString())
                .contains(testData.stream().collect(joining("\",\"", "\"", "\"")));

        // Verify rest of the flow
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("1")));
        commonUtilsMockedStatic.verify(() -> sleepMillis(VERIFICATION_DELAY_MILLIS));
        commonUtilsMockedStatic.verify(CommonUtils::captureScreen, times(1));
        verify(mockModel).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), eq("verification execution"));
    }

    @Test
    @DisplayName("Verification fails, should return failed result")
    void executeTestCaseVerificationFailsShouldReturnFailedResult() {
        // Given
        String verification = "Fail Verification";
        TestStep step = new TestStep("Action", null, verification);
        TestCase testCase = new TestCase("Verification Fail", null, List.of(step));
        String failMsg = "Verification failed";
        when(mockModel.generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), eq("verification execution")))
                .thenReturn(new VerificationExecutionResult(false, failMsg));

        // When
        TestExecutionResult result = Agent.executeTestCase(testCase);

        // Then
        assertThat(result.testExecutionStatus()).isEqualTo(FAILED);
        assertThat(result.stepResults()).hasSize(1);
        TestStepResult stepResult = result.stepResults().getFirst();
        assertThat(stepResult.success()).isFalse();
        assertThat(stepResult.errorMessage()).isEqualTo("Verification failed. %s".formatted(failMsg));
        assertThat(stepResult.screenshot()).isNotNull();
        assertThat(stepResult.executionStartTimestamp()).isNotNull();
        assertThat(stepResult.executionEndTimestamp()).isNotNull();

        verify(mockModel).generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), eq(ACTION_EXECUTION_PLAN_GENERATION));
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS)));
        verify(mockModel, atLeast(1)).generateAndGetResponseAsObject(
                any(VerificationExecutionPrompt.class), eq("verification execution"));
    }


    @Test
    @DisplayName("Action fails with no retry needed, should return failed result")
    void executeTestCaseActionWithErrorNoRetryShouldReturnFailedResult() {
        // Given
        String action = "Fail Action";
        TestStep step = new TestStep(action, null, "Verify");
        TestCase testCase = new TestCase("Action Fail Non-Retry", null, List.of(step));
        String failMsg = "Permanent tool failure";
        commonToolsMockedStatic.when(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS)))
                .thenReturn(new ToolExecutionResult(ERROR, failMsg, false));
        ArgumentCaptor<Map<String, String>> errorDetailsCaptor = ArgumentCaptor.forClass(Map.class);
        commonUtilsMockedStatic.when(() -> getObjectPrettyPrinted(any(), errorDetailsCaptor.capture())).thenReturn(of(failMsg));

        // When
        TestExecutionResult result = Agent.executeTestCase(testCase);

        // Then
        assertThat(result.testExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
        assertThat(result.stepResults()).hasSize(1);
        TestStepResult stepResult = result.stepResults().getFirst();
        assertThat(stepResult.success()).isFalse();
        assertThat(stepResult.errorMessage()).isEqualTo(format("Failure while executing action '%s'. Root cause: %s", action, failMsg));
        assertThat(stepResult.screenshot()).isNotNull();
        assertThat(stepResult.executionStartTimestamp()).isNotNull();
        assertThat(stepResult.executionEndTimestamp()).isNotNull();
        assertThat(errorDetailsCaptor.getValue()).containsExactly(Map.entry(MOCK_TOOL_NAME, failMsg));

        verify(mockModel).generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), eq(ACTION_EXECUTION_PLAN_GENERATION));
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS)));
        verify(mockModel, never()).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), anyString());
    }

    @Test
    @DisplayName("Tool execution throws exception, should return failed result")
    void executeTestCaseToolExecutionThrowsExceptionShouldReturnFailedResult() {
        // Given
        String action = "Exception Action";
        TestStep step = new TestStep(action, null, "Verify");
        TestCase testCase = new TestCase("Tool Exception Case", null, List.of(step));
        RuntimeException toolException = new RuntimeException("Tool exploded as expected");
        commonToolsMockedStatic.when(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS))).thenThrow(toolException);

        ArgumentCaptor<Map<String, String>> errorDetailsCaptor = ArgumentCaptor.forClass(Map.class);
        commonUtilsMockedStatic.when(() -> getObjectPrettyPrinted(any(), errorDetailsCaptor.capture())).thenReturn(of("mocked pretty printed error"));

        // When
        TestExecutionResult result = Agent.executeTestCase(testCase);

        // Then
        assertThat(result.testExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
        assertThat(result.stepResults()).hasSize(1);
        TestStepResult stepResult = result.stepResults().getFirst();
        assertThat(stepResult.success()).isFalse();
        assertThat(stepResult.errorMessage()).isEqualTo(format(
                "Failure while executing action '%s'. Root cause: %s",
                action, "mocked pretty printed error"));
        assertThat(stepResult.screenshot()).isNotNull();
        assertThat(stepResult.executionStartTimestamp()).isNotNull();
        assertThat(stepResult.executionEndTimestamp()).isNotNull();
        assertThat(errorDetailsCaptor.getValue()).containsExactly(
                Map.entry(MOCK_TOOL_NAME, format("'%s' tool execution failed because of internal error.", MOCK_TOOL_NAME)));

        verify(mockModel).generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), eq(ACTION_EXECUTION_PLAN_GENERATION));
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("1")));
        verify(mockModel, never()).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), anyString());
    }

    @Test
    @DisplayName("Invalid tool name requested by model, should return failed result")
    void executeTestCaseInvalidToolNameRequestedShouldReturnFailedResult() {
        // Given
        String action = "Invalid Tool Action";
        TestStep step = new TestStep(action, null, "Verify");
        TestCase testCase = new TestCase("Invalid Tool Case", null, List.of(step));
        String invalidToolName = "nonExistentTool";
        var invalidRequest = new ActionExecutionPlan(MOCK_UUID.toString(), invalidToolName, List.of());
        var testCaseExecutionPlan = new ExecutionPlan(List.of(invalidRequest));
        when(mockModel.generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), anyString()))
                .thenReturn(testCaseExecutionPlan);

        ArgumentCaptor<Map<String, String>> errorDetailsCaptor = ArgumentCaptor.forClass(Map.class);
        commonUtilsMockedStatic.when(() -> getObjectPrettyPrinted(any(), errorDetailsCaptor.capture())).thenReturn(of("mocked pretty printed error"));

        // When
        TestExecutionResult result = Agent.executeTestCase(testCase);

        // Then
        assertThat(result.testExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
        assertThat(result.stepResults()).hasSize(1);
        TestStepResult stepResult = result.stepResults().getFirst();
        assertThat(stepResult.success()).isFalse();
        assertThat(stepResult.errorMessage()).isEqualTo(format(
                "Failure while executing action '%s'. Root cause: %s",
                action, "mocked pretty printed error"));
        assertThat(stepResult.screenshot()).isNotNull();
        assertThat(stepResult.executionStartTimestamp()).isNotNull();
        assertThat(stepResult.executionEndTimestamp()).isNotNull();
        assertThat(errorDetailsCaptor.getValue()).containsExactly(
                Map.entry(invalidToolName, format("The requested tool '%s' is not registered, please fix the prompt", invalidToolName)));

        verify(mockModel).generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), eq(ACTION_EXECUTION_PLAN_GENERATION));
        verify(mockModel, never()).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), anyString());
    }

    @Test
    @DisplayName("Invalid arguments for tool, should return failed result")
    void executeTestCaseInvalidArgumentsShouldReturnFailedResult() {
        // Given
        String action = "Invalid Args Action";
        TestStep step = new TestStep(action, null, "Verify");
        TestCase testCase = new TestCase("Invalid Args Case", null, List.of(step));
        var invalidArgsRequest = new ActionExecutionPlan(MOCK_UUID.toString(), MOCK_TOOL_NAME, List.of("invalid"));
        var testCaseExecutionPlan = new ExecutionPlan(List.of(invalidArgsRequest));
        when(mockModel.generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), anyString())).thenReturn(
                testCaseExecutionPlan);
        String exceptionMessage = "For input string: \"invalid\"";
        commonToolsMockedStatic.when(() -> waitSeconds(eq("invalid"))).thenThrow(new IllegalArgumentException(exceptionMessage));
        ArgumentCaptor<Map<String, String>> errorDetailsCaptor = ArgumentCaptor.forClass(Map.class);
        commonUtilsMockedStatic.when(() -> getObjectPrettyPrinted(any(), errorDetailsCaptor.capture())).thenReturn(of(exceptionMessage));

        // When
        TestExecutionResult result = Agent.executeTestCase(testCase);

        // Then
        assertThat(result.testExecutionStatus()).isEqualTo(TestExecutionStatus.ERROR);
        assertThat(result.stepResults()).hasSize(1);
        TestStepResult stepResult = result.stepResults().getFirst();

        String failureText = format("Failure while executing action '%s'. Root cause: %s", action, exceptionMessage);
        assertThat(stepResult.errorMessage()).isEqualTo(failureText);
        assertThat(stepResult.screenshot()).isNotNull();
        assertThat(stepResult.executionStartTimestamp()).isNotNull();
        assertThat(stepResult.executionEndTimestamp()).isNotNull();
        assertThat(errorDetailsCaptor.getValue()).containsExactly(
                Map.entry(MOCK_TOOL_NAME, "Invalid arguments for tool '%s': %s".formatted(MOCK_TOOL_NAME, exceptionMessage)));

        verify(mockModel).generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), anyString());
        verify(mockModel, never()).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), anyString());
    }

    @Test
    @DisplayName("processVerificationRequest: Retries and succeeds")
    void processVerificationRequestRetriesAndSucceeds() {
        // Given
        TestStep step = new TestStep("Action", null, "Verify Retry");
        TestCase testCase = new TestCase("Verify Retry Success", null, List.of(step));
        String successMsg = "Verification finally OK";
        String failMsg = "Verification not ready";
        var toolExecutionRequest = new ActionExecutionPlan(MOCK_UUID.toString(), MOCK_TOOL_NAME, MOCK_TOOL_ARGS_LIST);
        var testCaseExecutionPlan = new ExecutionPlan(List.of(toolExecutionRequest));
        when(mockModel.generateAndGetResponseAsObject(any(ActionExecutionPlanPrompt.class), anyString()))
                .thenReturn(testCaseExecutionPlan);
        commonToolsMockedStatic.when(() -> waitSeconds(eq("1")))
                .thenReturn(new ToolExecutionResult(SUCCESS, "Action OK", false));
        when(mockModel.generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), eq("verification execution")))
                .thenReturn(new VerificationExecutionResult(false, failMsg)) // First call fails
                .thenReturn(new VerificationExecutionResult(true, successMsg)); // Second call succeeds

        // When
        TestExecutionResult result = Agent.executeTestCase(testCase);

        // Then
        assertThat(result.testExecutionStatus()).isEqualTo(PASSED);
        assertThat(result.executionStartTimestamp()).isNotNull();
        assertThat(result.executionEndTimestamp()).isNotNull();
        verify(mockModel, times(2)).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class),
                eq("verification execution"));
    }
}
