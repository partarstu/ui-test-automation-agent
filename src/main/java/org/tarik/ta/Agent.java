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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.dto.*;
import org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus;
import org.tarik.ta.helper_entities.ActionExecutionResult;
import org.tarik.ta.helper_entities.TestCase;
import org.tarik.ta.helper_entities.TestStep;
import org.tarik.ta.model.GenAiModel;
import org.tarik.ta.prompts.ActionExecutionPlanPrompt.Builder.ActionInfo;
import org.tarik.ta.prompts.PreconditionVerificationPrompt;
import org.tarik.ta.prompts.ActionExecutionPlanPrompt;
import org.tarik.ta.prompts.VerificationExecutionPrompt;
import org.tarik.ta.tools.AbstractTools.ToolExecutionResult;
import org.tarik.ta.tools.CommonTools;
import org.tarik.ta.tools.KeyboardTools;
import org.tarik.ta.tools.MouseTools;

import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static java.util.UUID.randomUUID;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static org.tarik.ta.AgentConfig.*;
import static org.tarik.ta.dto.TestExecutionResult.TestExecutionStatus.*;
import static org.tarik.ta.model.ModelFactory.getInstructionModel;
import static org.tarik.ta.model.ModelFactory.getVisionModel;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.ERROR;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.SUCCESS;
import static org.tarik.ta.utils.CommonUtils.*;
import static org.tarik.ta.utils.ImageUtils.saveImage;

public class Agent {
    private static final Logger LOG = LoggerFactory.getLogger(Agent.class);
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    protected static final Map<String, Tool> allToolsByName = getToolsByName();
    protected static final int TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS = getTestStepExecutionRetryTimeoutMillis();
    protected static final int VERIFICATION_RETRY_TIMEOUT_MILLIS = getVerificationRetryTimeoutMillis();
    protected static final int TEST_STEP_RETRY_INTERVAL_MILLIS = getTestStepExecutionRetryIntervalMillis();
    protected static final int ACTION_VERIFICATION_DELAY_MILLIS = getActionVerificationDelayMillis();
    private static final boolean DEBUG_MODE = isDebugMode();
    private static final String VERIFICATION_EXECUTION = "verification execution";
    private static final String PRECONDITION_VALIDATION = "precondition validation";
    private static final String ACTION_EXECUTION_PLAN = "action execution plan generation";

    private record TestStepById(String id, TestStep testStep) {
    }

    private record PreconditionById(String id, String precondition) {
    }

    public static TestExecutionResult executeTestCase(TestCase testCase) {
        var testExecutionStartTimestamp = now();
        List<TestStepResult> stepResults = new ArrayList<>();

        List<String> preconditions = testCase.preconditions();
        if (preconditions != null && !preconditions.isEmpty()) {
            LOG.info("Executing and verifying preconditions for test case: {}", testCase.name());
            List<PreconditionById> preconditionByIds = preconditions.stream()
                    .map(precondition -> new PreconditionById(randomUUID().toString(), precondition))
                    .toList();
            Map<String, String> preconditionByIdMap = preconditionByIds.stream()
                    .collect(toMap(PreconditionById::id, PreconditionById::precondition));
            var preconditionExecutionPlans = getPreconditionExecutionPlans(preconditionByIdMap);
            if (preconditionExecutionPlans == null || preconditionExecutionPlans.isEmpty()) {
                var errorMessage = "Could not generate execution plan for preconditions";
                return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, errorMessage, captureScreen(),
                        true);
            }
            if (preconditionExecutionPlans.size() != preconditions.size()) {
                var errorMessage = "Not all preconditions were included into the execution plan. Expected to have %d of them, but got %d."
                        .formatted(preconditions.size(), preconditionExecutionPlans.size());
                return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, errorMessage, captureScreen(),
                        true);
            }

            for (var preconditionExecutionPlan : preconditionExecutionPlans) {
                var precondition = preconditionByIdMap.get(preconditionExecutionPlan.actionUniqueId());
                LOG.info("Executing precondition: {}", precondition);
                var preconditionExecutionResult = processToolExecutionRequest(preconditionExecutionPlan);
                if (!preconditionExecutionResult.success()) {
                    var errorMessage = "Failure while executing precondition '%s'. Root cause: %s"
                            .formatted(preconditionExecutionPlan, preconditionExecutionResult.message());
                    return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage, captureScreen(),
                            true);
                }
                LOG.info("Precondition execution complete.");

                var preconditionValidationResult = processPreconditionValidationRequest(precondition);
                if (!preconditionValidationResult.success()) {
                    var errorMessage = "Precondition '%s' not fulfilled, although was executed. %s"
                            .formatted(precondition, preconditionValidationResult.message());
                    return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage, captureScreen(),
                            true);
                }
                LOG.info("Precondition '{}' is met.", precondition);
            }

            LOG.info("All preconditions are met for test case: {}", testCase.name());
        }

        List<TestStepById> testStepByIds = testCase.testSteps().stream()
                .map(testStep -> new TestStepById(randomUUID().toString(), testStep))
                .toList();
        Map<String, TestStep> testStepByUuidMap = testStepByIds.stream()
                .collect(toMap(TestStepById::id, TestStepById::testStep));
        List<ActionInfo> stepInfos = testStepByIds.stream()
                .map(testStepById -> new ActionInfo(testStepById.id(), testStepById.testStep().stepDescription(),
                        testStepById.testStep().testData()))
                .toList();
        var testCaseExecutionPlan = getActionExecutionPlan(stepInfos);
        for (var testStepExecutionPlan : testCaseExecutionPlan.actionExecutionPlans()) {
            var testStep = testStepByUuidMap.get(testStepExecutionPlan.actionUniqueId());
            var actionInstruction = testStep.stepDescription();
            var verificationInstruction = testStep.expectedResults();
            try {
                var executionStartTimestamp = now();
                var actionResult = processToolExecutionRequest(testStepExecutionPlan);
                if (!actionResult.success()) {
                    return getFailedActionResult(testCase, actionInstruction, actionResult, testStep, stepResults, executionStartTimestamp,
                            testExecutionStartTimestamp);
                }
                LOG.info("Action execution complete.");

                String actualResult = null;
                if (isNotBlank(verificationInstruction)) {
                    var verificationResult = executeVerification(verificationInstruction, actionInstruction);
                    if (!verificationResult.success()) {
                        return getFailedVerificationResult(testCase, verificationResult, testStep, stepResults, executionStartTimestamp,
                                testExecutionStartTimestamp);
                    }
                    LOG.info("Verification execution complete.");
                    actualResult = verificationResult.message();
                }

                stepResults.add(new TestStepResult(testStep, true, null, actualResult, null, executionStartTimestamp, now()));
            } catch (Exception e) {
                LOG.error("Unexpected error while executing the test step: '{}'", testStep.stepDescription(), e);
                addFailedTestStepWithScreenshot(testStep, stepResults, e.getMessage(), null, now(), now());
                return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, e.getMessage());
            }
        }
        return new TestExecutionResult(testCase.name(), PASSED, stepResults, null, testExecutionStartTimestamp, now(), null);
    }

    @NotNull
    private static TestExecutionResult getFailedActionResult(TestCase testCase, String actionInstruction,
                                                             ActionExecutionResult actionResult, TestStep testStep,
                                                             List<TestStepResult> stepResults, Instant executionStartTimestamp,
                                                             Instant testExecutionStartTimestamp) {
        var errorMessage = "Failure while executing action '%s'. Root cause: %s"
                .formatted(actionInstruction, actionResult.message());
        addFailedTestStepWithScreenshot(testStep, stepResults, errorMessage, null, executionStartTimestamp, now());
        return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, errorMessage);
    }

    @NotNull
    private static TestExecutionResult getFailedVerificationResult(TestCase testCase, VerificationExecutionResult verificationResult,
                                                                   TestStep testStep, List<TestStepResult> stepResults,
                                                                   Instant executionStartTimestamp, Instant testExecutionStartTimestamp) {
        var errorMessage = "Verification failed. %s".formatted(verificationResult.message());
        addFailedTestStepWithScreenshot(testStep, stepResults, errorMessage, verificationResult.message(),
                executionStartTimestamp, now());
        return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage);
    }

    private static VerificationExecutionResult executeVerification(String verificationInstruction, String actionInstruction) {
        sleepMillis(ACTION_VERIFICATION_DELAY_MILLIS);
        var finalInstruction = "Verify that:  \"%s\"".formatted(verificationInstruction);
        LOG.info("Executing verification: '{}'", finalInstruction);
        return processVerificationRequest(finalInstruction, actionInstruction);
    }

    @NotNull
    private static TestExecutionResult getFailedTestExecutionResult(TestCase testCase, List<TestStepResult> stepResults,
                                                                    Instant testExecutionStartTimestamp, String errorMessage,
                                                                    BufferedImage screenshot, boolean logMessage) {
        if (logMessage) {
            LOG.error(errorMessage);
        }
        return new TestExecutionResult(testCase.name(), FAILED, stepResults, screenshot, testExecutionStartTimestamp,
                now(), errorMessage);
    }

    private static TestExecutionResult getFailedTestExecutionResult(TestCase testCase, List<TestStepResult> stepResults,
                                                                    Instant testExecutionStartTimestamp, String errorMessage) {
        return getFailedTestExecutionResult(testCase, stepResults, testExecutionStartTimestamp, errorMessage, null, false);
    }

    private static List<ActionExecutionPlan> getPreconditionExecutionPlans(Map<String, String> preconditionsById) {
        List<ActionInfo> stepInfos = preconditionsById.entrySet().stream()
                .map(preconditionById -> new ActionInfo(preconditionById.getKey(), preconditionById.getValue(), List.of()))
                .toList();
        var preconditionExecutionPlan = getActionExecutionPlan(stepInfos);
        return preconditionExecutionPlan.actionExecutionPlans();
    }

    @NotNull
    private static TestExecutionResult getTestExecutionResultWithError(TestCase testCase, List<TestStepResult> stepResults,
                                                                       Instant testExecutionStartTimestamp, String errorMessage,
                                                                       BufferedImage screenshot, boolean logMessage) {
        if (logMessage) {
            LOG.error(errorMessage);
        }
        return new TestExecutionResult(testCase.name(), TestExecutionStatus.ERROR, stepResults, screenshot,
                testExecutionStartTimestamp, now(), errorMessage);
    }

    @NotNull
    private static TestExecutionResult getTestExecutionResultWithError(TestCase testCase, List<TestStepResult> stepResults,
                                                                       Instant testExecutionStartTimestamp, String errorMessage) {
        return getTestExecutionResultWithError(testCase, stepResults, testExecutionStartTimestamp, errorMessage, null, false);
    }

    private static void addFailedTestStepWithScreenshot(TestStep testStep, List<TestStepResult> stepResults, String errorMessage,
                                                        String actualResult, Instant executionStartTimestamp,
                                                        Instant executionEndTimestamp) {
        var screenshot = captureScreen();
        stepResults.add(new TestStepResult(testStep, false, errorMessage, actualResult, screenshot, executionStartTimestamp,
                executionEndTimestamp));
    }

    private static ActionExecutionResult processToolExecutionRequest(ActionExecutionPlan actionExecutionPlan) {
        var deadline = now().plusMillis(TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS);
        Map<String, String> toolExecutionInfoByToolName = new HashMap<>();

        LOG.info("Executing tool execution request '{}'", actionExecutionPlan);
        while (true) {
            try {
                var toolExecutionResult = executeRequestedTool(actionExecutionPlan.toolName(), actionExecutionPlan.arguments());
                if (toolExecutionResult.executionStatus() == SUCCESS) {
                    toolExecutionInfoByToolName.put(actionExecutionPlan.toolName(), toolExecutionResult.message());
                    break;
                } else if (!toolExecutionResult.retryMakesSense()) {
                    LOG.info("Tool execution failed and retry doesn't make sense. Interrupting the execution.");
                    toolExecutionInfoByToolName.put(actionExecutionPlan.toolName(), toolExecutionResult.message());
                    return getFailedActionExecutionResult(toolExecutionInfoByToolName);
                } else {
                    LOG.info("Tool execution wasn't successful, retrying. Root cause: {}", toolExecutionResult.message());
                    var nextRetryMoment = getNextRetryMoment(TEST_STEP_RETRY_INTERVAL_MILLIS);
                    if (nextRetryMoment.isBefore(deadline)) {
                        waitUntil(nextRetryMoment);
                    } else {
                        LOG.warn("Tool execution retries exhausted, interrupting the execution.");
                        toolExecutionInfoByToolName.put(actionExecutionPlan.toolName(), toolExecutionResult.message());
                        return getFailedActionExecutionResult(toolExecutionInfoByToolName);
                    }
                }
            } catch (Exception e) {
                LOG.error("Got exception while invoking requested tools:", e);
                toolExecutionInfoByToolName.put(actionExecutionPlan.toolName(), e.getLocalizedMessage());
                return getFailedActionExecutionResult(toolExecutionInfoByToolName);
            }
        }


        return new ActionExecutionResult(true, getToolExecutionDetails(toolExecutionInfoByToolName));
    }

    private static @NotNull ExecutionPlan getActionExecutionPlan(List<ActionInfo> actions) {
        var toolSpecs = allToolsByName.values().stream().map(Tool::toolSpecification).toList();
        try (var model = getActionProcessingModel()) {
            var prompt = ActionExecutionPlanPrompt.builder()
                    .withActions(actions)
                    .withToolSpecs(toolSpecs)
                    .build();
            return model.generateAndGetResponseAsObject(prompt, ACTION_EXECUTION_PLAN);
        }
    }

    @NotNull
    private static ActionExecutionResult getFailedActionExecutionResult(Map<String, String> toolExecutionInfoByToolName) {
        return new ActionExecutionResult(false, getToolExecutionDetails(toolExecutionInfoByToolName));
    }

    @NotNull
    private static String getToolExecutionDetails(Map<String, String> toolExecutionInfoByToolName) {
        return getObjectPrettyPrinted(OBJECT_MAPPER, toolExecutionInfoByToolName).orElse("None");
    }


    private static VerificationExecutionResult processVerificationRequest(@NotNull String verification,
                                                                          @NotNull String actionIncludingData) {
        return executeWithRetries(VERIFICATION_RETRY_TIMEOUT_MILLIS, TEST_STEP_RETRY_INTERVAL_MILLIS,
                VERIFICATION_EXECUTION, (model) -> {
            var screenshot = captureScreen();
            if (DEBUG_MODE) {
                saveImage(screenshot, "verification");
            }
            var prompt = VerificationExecutionPrompt.builder()
                    .withVerificationDescription(verification)
                    .withActionDescription(actionIncludingData)
                    .screenshot(screenshot)
                    .build();
            return model.generateAndGetResponseAsObject(prompt, VERIFICATION_EXECUTION);
        }, VerificationExecutionResult::success, Agent::getVerificationExecutionModel);
    }

    private static <T> T executeWithRetries(long retryTimeoutMillis, long retryIntervalMillis, String operationDescription,
                                            Function<GenAiModel, T> resultSupplier,
                                            Function<T, Boolean> successPredicate, Supplier<GenAiModel> executionModelSupplier) {
        var deadline = now().plusMillis(retryTimeoutMillis);
        T result;
        boolean retryActive;
        try (var model = executionModelSupplier.get()) {
            do {
                result = resultSupplier.apply(model);
                LOG.info("Result of {} : <{}>", operationDescription, result);
                if (successPredicate.apply(result)) {
                    return result;
                } else {
                    retryActive = now().isBefore(deadline);
                    if (retryActive) {
                        waitTillNextRetry("The %s failed".formatted(operationDescription), deadline,retryIntervalMillis);
                    }
                }
            } while (retryActive);
        }
        return result;
    }

    private static VerificationExecutionResult processPreconditionValidationRequest(String precondition) {
        return executeWithRetries(VERIFICATION_RETRY_TIMEOUT_MILLIS, 0, PRECONDITION_VALIDATION, (model) -> {
            var prompt = PreconditionVerificationPrompt.builder()
                    .withPreconditionDescription(precondition)
                    .screenshot(captureScreen())
                    .build();
            LOG.info("Checking if precondition is met: '{}'", precondition);
            return model.generateAndGetResponseAsObject(prompt, PRECONDITION_VALIDATION);
        }, VerificationExecutionResult::success, Agent::getVerificationExecutionModel);
    }

    private static void waitTillNextRetry(String retryCause, Instant deadline, long retryIntervalMillis) {
        LOG.info("{}, retrying within configured deadline.", retryCause);
        var nextRetryMoment = getNextRetryMoment(retryIntervalMillis);
        if (nextRetryMoment.isBefore(deadline)) {
            waitUntil(nextRetryMoment);
        }
    }

    private static Instant getNextRetryMoment(long retryIntervalMillis) {
        return now().plusMillis(retryIntervalMillis);
    }

    private static ToolExecutionResult executeRequestedTool(String toolName, List<String> args) {
        LOG.info("Model requested an execution of the tool '{}' with the following arguments: <{}>", toolName, args);
        var tool = getTool(toolName);
        Class<?> toolClass = tool.clazz();
        int paramsAmount = tool.toolSpecification().parameters().properties().size();
        checkArgument(paramsAmount == args.size(),
                "Model requested tool '%s' with %s arguments, but the tool requires %s arguments.",
                toolName, args.size(), paramsAmount);
        var method = getToolClassMethod(toolClass, toolName, paramsAmount);
        var arguments = args.toArray();
        return getToolExecutionResult(toolName, arguments, method, toolClass);
    }

    private static ToolExecutionResult getToolExecutionResult(String toolName, Object[] arguments, Method method, Class<?> toolClass) {
        try {
            var result = (ToolExecutionResult) method.invoke(toolClass, arguments);
            LOG.info("Tool execution completed '{}' using arguments: <{}>", toolName, Arrays.toString(arguments));
            return result;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Access denied while invoking tool '%s'".formatted(toolName), e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof IllegalArgumentException illegalArgumentException) {
                LOG.error("Illegal arguments provided for tool '{}'", toolName, illegalArgumentException);
                return new ToolExecutionResult(ERROR, "Invalid arguments for tool '%s': %s"
                        .formatted(toolName, illegalArgumentException.getMessage()), false);
            } else {
                LOG.error("Exception thrown by tool '{}': {}", toolName, (ofNullable(e.getMessage()).orElse("Unknown Cause")), e);
                throw new RuntimeException("'%s' tool execution failed because of internal error.".formatted(toolName), e);
            }
        }
    }

    private static Tool getTool(String toolName) {
        if (!allToolsByName.containsKey(toolName)) {
            throw new IllegalArgumentException(
                    "The requested tool '%s' is not registered, please fix the prompt".formatted(toolName));
        }
        return allToolsByName.get(toolName);
    }

    @NotNull
    private static Method getToolClassMethod(Class<?> toolClass, String toolName, int paramsAmount) {
        try {
            var paramTypes = range(0, paramsAmount)
                    .mapToObj(_ -> String.class)
                    .toArray(Class<?>[]::new);
            return toolClass.getMethod(toolName, paramTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Tool> getTools(Class<?> clazz) {
        return toolSpecificationsFrom(clazz).stream()
                .map(toolSpecification -> new Tool(toolSpecification.name(), toolSpecification, clazz))
                .toList();
    }

    private static GenAiModel getActionProcessingModel() {
        return getInstructionModel();
    }

    private static GenAiModel getVerificationExecutionModel() {
        return getVisionModel();
    }

    private static Map<String, Tool> getToolsByName() {
        return Stream.of(KeyboardTools.class, MouseTools.class, CommonTools.class)
                .map(Agent::getTools)
                .flatMap(Collection::stream)
                .collect(toMap(Tool::name, identity()));
    }

    protected record Tool(String name, ToolSpecification toolSpecification, Class<?> clazz) {
    }
}
