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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tarik.ta.dto.VerificationExecutionResult;
import org.tarik.ta.exceptions.ActionExecutionException;
import org.tarik.ta.exceptions.VerificationExecutionException;
import org.tarik.ta.helper_entities.TestCase;
import org.tarik.ta.helper_entities.TestStep;
import org.tarik.ta.model.GenAiModel;
import org.tarik.ta.model.ModelFactory;
import org.tarik.ta.prompts.ActionExecutionPrompt;
import org.tarik.ta.prompts.VerificationExecutionPrompt;
import org.tarik.ta.tools.AbstractTools.ToolExecutionResult;
import org.tarik.ta.tools.CommonTools;
import org.tarik.ta.utils.CommonUtils;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.*;
import static org.tarik.ta.tools.CommonTools.waitSeconds;
import static org.tarik.ta.utils.CommonUtils.sleepMillis;

@ExtendWith(MockitoExtension.class)
class AgentTest {
    @Mock
    private GenAiModel mockModel;
    @Mock
    private AiMessage mockAiMessage;
    @Mock
    private ChatResponse mockChatResponse;
    @Mock
    private BufferedImage mockScreenshot;

    // Static mocks
    private MockedStatic<ModelFactory> modelFactoryMockedStatic;
    private MockedStatic<CommonUtils> commonUtilsMockedStatic;
    private MockedStatic<AgentConfig> agentConfigMockedStatic;
    private MockedStatic<CommonTools> commonToolsMockedStatic;

    // Constants for configuration
    private static final int TEST_STEP_TIMEOUT_MILLIS = 50;
    private static final int VERIFICATION_TIMEOUT_MILLIS = 50;
    private static final int RETRY_INTERVAL_MILLIS = 10;
    private static final int VERIFICATION_DELAY_MILLIS = 5;
    private static final int TOOL_PARAM_WAIT_AMOUNT_SECONDS = 1;
    private static final String MOCK_TOOL_NAME = "waitSeconds";
    private static final String MOCK_TOOL_ARGS = "{\"arg0\":\"%d\"}".formatted(TOOL_PARAM_WAIT_AMOUNT_SECONDS);
    private static final String MOCK_TOOL_ID = "mockToolId123";


    @BeforeEach
    void setUp() {

        modelFactoryMockedStatic = mockStatic(ModelFactory.class);
        commonUtilsMockedStatic = mockStatic(CommonUtils.class);
        agentConfigMockedStatic = mockStatic(AgentConfig.class);
        commonToolsMockedStatic = mockStatic(CommonTools.class);

        // Model Factory
        modelFactoryMockedStatic.when(ModelFactory::getInstructionModel).thenReturn(mockModel);
        modelFactoryMockedStatic.when(ModelFactory::getVisionModel).thenReturn(mockModel);

        // Common Utils
        commonUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(anyString())).thenCallRealMethod();
        commonUtilsMockedStatic.when(() -> CommonUtils.isNotBlank(isNull())).thenReturn(false);
        commonUtilsMockedStatic.when(CommonUtils::captureScreen).thenReturn(mockScreenshot);
        commonUtilsMockedStatic.when(() -> sleepMillis(anyInt())).thenAnswer(_ -> null); // No actual sleep
        commonUtilsMockedStatic.when(() -> CommonUtils.waitUntil(any(Instant.class))).thenAnswer(_ -> null);
        commonToolsMockedStatic.when(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS)))
                .thenReturn(new ToolExecutionResult(SUCCESS, "Wait completed", false));

        // Agent Config
        agentConfigMockedStatic.when(AgentConfig::getTestStepExecutionRetryTimeoutMillis).thenReturn(TEST_STEP_TIMEOUT_MILLIS);
        agentConfigMockedStatic.when(AgentConfig::getVerificationRetryTimeoutMillis).thenReturn(VERIFICATION_TIMEOUT_MILLIS);
        agentConfigMockedStatic.when(AgentConfig::getTestStepExecutionRetryIntervalMillis).thenReturn(RETRY_INTERVAL_MILLIS);
        agentConfigMockedStatic.when(AgentConfig::getActionVerificationDelayMillis).thenReturn(VERIFICATION_DELAY_MILLIS);


        lenient().when(mockModel.generate(any(ActionExecutionPrompt.class), anyList(), eq("action execution")))
                .thenReturn(mockChatResponse);
        lenient().when(mockModel.generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class),
                        eq("verification execution")))
                .thenReturn(new VerificationExecutionResult(true, "Verification successful"));
        lenient().when(mockChatResponse.aiMessage()).thenReturn(mockAiMessage);
        lenient().when(mockAiMessage.hasToolExecutionRequests()).thenReturn(true);
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id(MOCK_TOOL_ID)
                .name(MOCK_TOOL_NAME)
                .arguments(MOCK_TOOL_ARGS)
                .build();
        lenient().when(mockAiMessage.toolExecutionRequests()).thenReturn(List.of(toolRequest));
    }

    @AfterEach
    void tearDown() {
        // Close static mocks
        modelFactoryMockedStatic.close();
        commonUtilsMockedStatic.close();
        agentConfigMockedStatic.close();
        commonToolsMockedStatic.close();
    }

    @Test
    @DisplayName("Single test step with action and successful verification")
    void singleStepActionAndVerificationSuccess() {
        // Given
        TestStep step = new TestStep("Perform Action", null, "Verify Result");
        TestCase testCase = new TestCase("Single Step Success", List.of(step));

        // When
        Agent.executeTestCase(testCase);

        // Then
        verify(mockModel).generate(any(ActionExecutionPrompt.class), anyList(), eq("action execution"));
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
        TestCase testCase = new TestCase("Single Action Only", List.of(step));

        // When
        Agent.executeTestCase(testCase);

        // Then
        verify(mockModel).generate(any(ActionExecutionPrompt.class), anyList(), eq("action execution"));
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS)));
        commonUtilsMockedStatic.verify(() -> sleepMillis(anyInt()), never());
        commonUtilsMockedStatic.verify(CommonUtils::captureScreen, never());
        verify(mockModel, never()).generateAndGetResponseAsObject(any(), any());
    }

    @Test
    @DisplayName("Multiple steps with actions and successful verifications including test data")
    void multipleStepsIncludingTestDataSuccess() {
        // Given
        TestStep step1 = new TestStep("Action 1", null, "Verify 1");
        TestStep step2 = new TestStep("Action 2", List.of("data"), "Verify 2"); // With test data
        TestCase testCase = new TestCase("Multi-Step Success", List.of(step1, step2));
        commonToolsMockedStatic.when(() -> waitSeconds(eq("1")))
                .thenReturn(new ToolExecutionResult(SUCCESS, "Wait 1 OK", false))
                .thenReturn(new ToolExecutionResult(SUCCESS, "Wait 2 OK", false));
        when(mockModel.generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), eq("verification execution")))
                .thenReturn(new VerificationExecutionResult(true, "Verify 1 OK"))
                .thenReturn(new VerificationExecutionResult(true, "Verify 2 OK"));

        // When
        Agent.executeTestCase(testCase);

        // Then
        verify(mockModel, times(2)).generate(any(ActionExecutionPrompt.class), anyList(), eq("action execution"));
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("1")), times(2));
        commonUtilsMockedStatic.verify(() -> sleepMillis(VERIFICATION_DELAY_MILLIS), times(2));
        commonUtilsMockedStatic.verify(CommonUtils::captureScreen, times(2)); // Corrected to times(2)
        verify(mockModel, times(2)).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class),
                eq("verification execution"));
    }

    @Test
    @DisplayName("Single step with action using test data")
    void singleStepWithDataSuccess() {
        // Given
        List<String> testData = List.of("input1", "input2");
        TestStep step = new TestStep("Action With Data", testData, "Verify Data Action");
        TestCase testCase = new TestCase("Action Data Success", List.of(step));
        String expectedInstructionFragment = "using following input data: 'input1', 'input2'";
        ArgumentCaptor<ActionExecutionPrompt> promptCaptor = forClass(ActionExecutionPrompt.class);

        // When
        Agent.executeTestCase(testCase);

        // Then
        verify(mockModel).generate(promptCaptor.capture(), anyList(), eq("action execution"));
        assertThat(promptCaptor.getValue().getUserMessage().singleText()).contains(expectedInstructionFragment);

        // Verify rest of the flow
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("1")));
        commonUtilsMockedStatic.verify(() -> sleepMillis(VERIFICATION_DELAY_MILLIS));
        commonUtilsMockedStatic.verify(CommonUtils::captureScreen);
        verify(mockModel).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), eq("verification execution"));
    }

    @Test
    @DisplayName("Verification fails, should throw exception")
    void executeTestCaseVerificationFailsShouldThrowException() {
        // Given
        String verification = "Fail Verification";
        TestStep step = new TestStep("Action", null, verification);
        TestCase testCase = new TestCase("Verification Fail", List.of(step));
        String failMsg = "Verification failed";
        when(mockModel.generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), eq("verification execution")))
                .thenReturn(new VerificationExecutionResult(false, failMsg));

        // When & Then
        assertThatThrownBy(() -> Agent.executeTestCase(testCase)).isInstanceOf(VerificationExecutionException.class)
                .hasMessageMatching("Verifying that %s failed. %s. Interrupting test case execution.".formatted(verification, failMsg));
        verify(mockModel).generate(any(ActionExecutionPrompt.class), anyList(), eq("action execution"));
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS)));
        verify(mockModel, atLeast(2)).generateAndGetResponseAsObject(
                any(VerificationExecutionPrompt.class), eq("verification execution"));
    }


    @Test
    @DisplayName("Action fails with no retry needed, should throw exception")
    void executeTestCaseActionWithErrorNoRetryShouldThrowException() {
        // Given
        String action = "Fail Action";
        TestStep step = new TestStep(action, null, "Verify");
        TestCase testCase = new TestCase("Action Fail Non-Retry", List.of(step));
        String failMsg = "Permanent tool failure";
        commonToolsMockedStatic.when(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS)))
                .thenReturn(new ToolExecutionResult(ERROR, failMsg, false));

        // When & Then
        assertThatThrownBy(() -> Agent.executeTestCase(testCase)).isInstanceOf(ActionExecutionException.class)
                .hasMessage("Failure while executing '%s'. Root cause: %s. Interrupting test case execution."
                        .formatted(action, failMsg));
        verify(mockModel).generate(any(ActionExecutionPrompt.class), anyList(), eq("action execution"));
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS)));
        verify(mockModel, never()).generateAndGetResponseAsObject(any(), any());
    }

    @Test
    @DisplayName("run: Tool execution throws exception, should throw exception")
    void executeTestCaseToolExecutionThrowsExceptionShouldThrowException() {
        // Given
        String action = "Exception Action";
        TestStep step = new TestStep(action, null, "Verify");
        TestCase testCase = new TestCase("Tool Exception Case", List.of(step));
        RuntimeException toolException = new RuntimeException("Tool exploded as expected");
        commonToolsMockedStatic.when(() -> waitSeconds(eq("" + TOOL_PARAM_WAIT_AMOUNT_SECONDS))).thenThrow(toolException);

        // When & Then
        assertThatThrownBy(() -> Agent.executeTestCase(testCase)).isInstanceOf(ActionExecutionException.class)
                .hasMessageMatching(("Failure while executing '%s'. Root cause: 'waitSeconds' tool execution failed. The cause: %s. " +
                        "Interrupting test case execution.").formatted(action, toolException.getMessage()));

        verify(mockModel).generate(any(ActionExecutionPrompt.class), anyList(), eq("action execution"));
        commonToolsMockedStatic.verify(() -> waitSeconds(eq("1")));
        verify(mockModel, never()).generateAndGetResponseAsObject(any(), any());
    }

    @Test
    @DisplayName("run: Invalid tool name requested by model, should throw exception")
    void executeTestCaseInvalidToolNameRequestedShouldThrowException() {
        // Given
        String action = "Invalid Tool Action";
        TestStep step = new TestStep(action, null, "Verify");
        TestCase testCase = new TestCase("Invalid Tool Case", List.of(step));
        String invalidToolName = "nonExistentTool";
        var invalidRequest = ToolExecutionRequest.builder()
                .id(MOCK_TOOL_ID)
                .name(invalidToolName)
                .arguments("{}")
                .build();
        when(mockAiMessage.toolExecutionRequests()).thenReturn(List.of(invalidRequest));

        // When & Then
        assertThatThrownBy(() -> Agent.executeTestCase(testCase)).isInstanceOf(ActionExecutionException.class)
                .hasMessageMatching(("Failure while executing '%s'. Root cause: The requested tool 'nonExistentTool' is not registered, " +
                        "please fix the prompt. Interrupting test case execution.").formatted(action));

        verify(mockModel).generate(any(ActionExecutionPrompt.class), anyList(), eq("action execution"));
        verify(mockModel, never()).generateAndGetResponseAsObject(any(), any());
    }

    @Test
    @DisplayName("run: Invalid JSON arguments for tool, should throw exception")
    void executeTestCaseInvalidJsonArgumentsShouldThrowException() {
        // Given
        String action = "Invalid Args Action";
        TestStep step = new TestStep(action, null, "Verify");
        TestCase testCase = new TestCase("Invalid Args Case", List.of(step));
        String invalidJson = "this is not json";
        ToolExecutionRequest invalidArgsRequest = ToolExecutionRequest.builder()
                .id(MOCK_TOOL_ID)
                .name(MOCK_TOOL_NAME)
                .arguments(invalidJson)
                .build();
        when(mockAiMessage.toolExecutionRequests()).thenReturn(List.of(invalidArgsRequest));

        // When & Then
        assertThatThrownBy(() -> Agent.executeTestCase(testCase)).isInstanceOf(ActionExecutionException.class)
                .hasMessageMatching(("Failure while executing '%s'. Root cause: .*IllegalStateException.*Interrupting test case " +
                        "execution.").formatted(action));

        verify(mockModel).generate(any(ActionExecutionPrompt.class), anyList(), eq("action execution")); // Corrected to times(1)
        verify(mockModel, never()).generateAndGetResponseAsObject(any(), any());
    }

    @Test
    @DisplayName("processVerificationRequest: Retries and succeeds")
    void processVerificationRequestRetriesAndSucceeds() {
        // Given
        TestStep step = new TestStep("Action", null, "Verify Retry");
        TestCase testCase = new TestCase("Verify Retry Success", List.of(step));
        String successMsg = "Verification finally OK";
        String failMsg = "Verification not ready";
        commonToolsMockedStatic.when(() -> waitSeconds(eq("1")))
                .thenReturn(new ToolExecutionResult(SUCCESS, "Action OK", false));
        when(mockModel.generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class), eq("verification execution")))
                .thenReturn(new VerificationExecutionResult(false, failMsg)) // First call fails
                .thenReturn(new VerificationExecutionResult(true, successMsg)); // Second call succeeds

        // When
        Agent.executeTestCase(testCase);

        // Then
        verify(mockModel, times(2)).generateAndGetResponseAsObject(any(VerificationExecutionPrompt.class),
                eq("verification execution"));
    }
}