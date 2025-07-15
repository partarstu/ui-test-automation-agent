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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.tarik.ta.prompts.PreconditionVerificationPrompt;
import org.tarik.ta.prompts.TestCaseExecutionPlanPrompt;
import org.tarik.ta.prompts.TestCaseExecutionPlanPrompt.Builder.TestStepInfo;
import org.tarik.ta.prompts.VerificationExecutionPrompt;
import org.tarik.ta.tools.AbstractTools.ToolExecutionResult;
import org.tarik.ta.tools.CommonTools;
import org.tarik.ta.tools.KeyboardTools;
import org.tarik.ta.tools.MouseTools;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom;
import static java.lang.System.exit;
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
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.SUCCESS;
import static org.tarik.ta.utils.CommonUtils.*;
import static org.tarik.ta.utils.ImageUtils.saveScreenshot;

public class Agent {
    private static final Logger LOG = LoggerFactory.getLogger(Agent.class);
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    protected static final Map<String, Tool> allToolsByName = getToolsByName();
    protected static final int TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS = getTestStepExecutionRetryTimeoutMillis();
    protected static final int VERIFICATION_RETRY_TIMEOUT_MILLIS = getVerificationRetryTimeoutMillis();
    protected static final int TEST_STEP_RETRY_INTERVAL_MILLIS = getTestStepExecutionRetryIntervalMillis();
    protected static final int ACTION_VERIFICATION_DELAY_MILLIS = getActionVerificationDelayMillis();
    private static final boolean DEBUG_MODE = AgentConfig.isDebugMode();
    private static final int ERROR_STATUS = 1;
    private static final String VERIFICATION_EXECUTION = "verification execution";
    private static final String PRECONDITION_VALIDATION = "precondition validation";
    private static final String TEST_CASE_EXECUTION_PLAN = "test case execution plan generation";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Arguments missing: path to the file which contains JSON");
            exit(ERROR_STATUS);
        }
        String useCaseJsonPath = args[0];
        deserializeJsonFromFile(useCaseJsonPath, TestCase.class).ifPresentOrElse(testCase -> {
            LOG.info("Starting Agent execution for test case: {}", testCase.name());
            TestExecutionResult result = executeTestCase(testCase);
            LOG.info("Finished Agent execution for test case: {}", testCase.name());
            System.out.println(result);
            if (result.getTestExecutionStatus() != PASSED) {
                exit(ERROR_STATUS);
            }
        }, () -> {
            var message = "Failed to load test case from: " + useCaseJsonPath;
            LOG.error(message);
            exit(ERROR_STATUS);
        });
    }

    private record TestStepById(String id, TestStep testStep) {
    }

    public static TestExecutionResult executeTestCase(TestCase testCase) {
        var testExecutionStartTimestamp = now();
        List<TestStepResult> stepResults = new ArrayList<>();

        if (isNotBlank(testCase.preconditions())) {
            LOG.info("Verifying preconditions for test case: {}", testCase.name());
            var preconditionResult = processPreconditionValidationRequest(testCase.preconditions());
            if (!preconditionResult.success()) {
                var errorMessage = "Preconditions not met. %s".formatted(preconditionResult.message());
                var screenshot = captureScreen();
                LOG.error(errorMessage);
                return new TestExecutionResult(testCase.name(), FAILED, stepResults, screenshot, testExecutionStartTimestamp, now(),
                        errorMessage);
            }
            LOG.info("Preconditions met for test case: {}", testCase.name());
        }

        List<TestStepById> testStepByIds = testCase.testSteps().stream()
                .map(testStep -> new TestStepById(randomUUID().toString(), testStep))
                .toList();
        Map<String, TestStep> testStepByUuidMap = testStepByIds.stream()
                .collect(toMap(TestStepById::id, TestStepById::testStep));
        List<TestStepInfo> testStepInfos = testStepByIds.stream()
                .map(testStepById -> new TestStepInfo(testStepById.id(), testStepById.testStep().stepDescription(),
                        testStepById.testStep().testData()))
                .toList();
        var testCaseExecutionPlan = getTestCaseExecutionPlan(testStepInfos);
        for (var testStepExecutionPlan : testCaseExecutionPlan.testStepExecutionPlans()) {
            var testStep = testStepByUuidMap.get(testStepExecutionPlan.testStepUniqueId());
            var actionInstruction = testStep.stepDescription();
            var verificationInstruction = testStep.expectedResults();

            try {
                var executionStartTimestamp = now();
                var actionResult = processToolExecutionRequests(testStepExecutionPlan);
                if (!actionResult.success()) {
                    var errorMessage = "Failure while executing action '%s'. Root cause: %s"
                            .formatted(actionInstruction, actionResult.message());
                    addFailedTestStepWithScreenshot(testStep, stepResults, errorMessage, null, executionStartTimestamp, now());
                    return new TestExecutionResult(testCase.name(), TestExecutionStatus.ERROR, stepResults, captureScreen(),
                            testExecutionStartTimestamp, now(), errorMessage);
                }
                LOG.info("Action execution complete.");

                String actualResult = null;
                if (isNotBlank(verificationInstruction)) {
                    sleepMillis(ACTION_VERIFICATION_DELAY_MILLIS);
                    var finalInstruction = "Verify that:  \"%s\"".formatted(verificationInstruction);
                    LOG.info("Executing verification: '{}'", finalInstruction);
                    var verificationResult = processVerificationRequest(finalInstruction, actionInstruction);
                    if (!verificationResult.success()) {
                        var errorMessage = "Verification failed. %s"               .formatted(verificationResult.message());
                        addFailedTestStepWithScreenshot(testStep, stepResults, errorMessage, verificationResult.message(),
                                executionStartTimestamp, now());
                        return new TestExecutionResult(testCase.name(), FAILED, stepResults, captureScreen(), testExecutionStartTimestamp,
                                now(), errorMessage);
                    }
                    LOG.info("Verification execution complete.");
                    actualResult = verificationResult.message();
                }

                stepResults.add(new TestStepResult(testStep, true, null, actualResult, null, executionStartTimestamp, now()));
            } catch (Exception e) {
                LOG.error("Unexpected error while executing the test step: '{}'", testStep.stepDescription(), e);
                addFailedTestStepWithScreenshot(testStep, stepResults, e.getMessage(), null, now(), now());
                return new TestExecutionResult(testCase.name(), TestExecutionStatus.ERROR, stepResults, captureScreen(),
                        testExecutionStartTimestamp,
                        now(), e.getMessage());
            }
        }
        return new TestExecutionResult(testCase.name(), PASSED, stepResults, null, testExecutionStartTimestamp, now(), null);
    }

    private static void addFailedTestStepWithScreenshot(TestStep testStep, List<TestStepResult> stepResults, String errorMessage,
                                                        String actualResult, Instant executionStartTimestamp,
                                                        Instant executionEndTimestamp) {
        var screenshot = captureScreen();
        stepResults.add(new TestStepResult(testStep, false, errorMessage, actualResult, screenshot, executionStartTimestamp,
                executionEndTimestamp));
    }

    private static ActionExecutionResult processToolExecutionRequests(TestStepExecutionPlan stepExecutionPlan) {
        var deadline = now().plusMillis(TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS);
        Map<String, String> toolExecutionInfoByToolName = new HashMap<>();

        LOG.info("Executing tool execution request '{}'", stepExecutionPlan);
        while (true) {
            try {
                var toolExecutionResult = executeRequestedTool(stepExecutionPlan.toolName(), stepExecutionPlan.arguments());
                if (toolExecutionResult.executionStatus() == SUCCESS) {
                    toolExecutionInfoByToolName.put(stepExecutionPlan.toolName(), toolExecutionResult.message());
                    break;
                } else if (!toolExecutionResult.retryMakesSense()) {
                    LOG.info("Tool execution failed and retry doesn't make sense. Interrupting the execution.");
                    toolExecutionInfoByToolName.put(stepExecutionPlan.toolName(), toolExecutionResult.message());
                    return getFailedActionExecutionResult(toolExecutionInfoByToolName);
                } else {
                    LOG.info("Tool execution wasn't successful, retrying. Root cause: {}", toolExecutionResult.message());
                    var nextRetryMoment = getNextRetryMoment();
                    if (nextRetryMoment.isBefore(deadline)) {
                        waitUntil(nextRetryMoment);
                    } else {
                        LOG.warn("Tool execution retries exhausted, interrupting the execution.");
                        toolExecutionInfoByToolName.put(stepExecutionPlan.toolName(), toolExecutionResult.message());
                        return getFailedActionExecutionResult(toolExecutionInfoByToolName);
                    }
                }
            } catch (Exception e) {
                LOG.error("Got exception while invoking requested tools:", e);
                toolExecutionInfoByToolName.put(stepExecutionPlan.toolName(), e.getLocalizedMessage());
                return getFailedActionExecutionResult(toolExecutionInfoByToolName);
            }
        }


        return new ActionExecutionResult(true, getToolExecutionDetails(toolExecutionInfoByToolName));
    }

    private static @NotNull TestCaseExecutionPlan getTestCaseExecutionPlan(List<TestStepInfo> testSteps) {
        var toolSpecs = allToolsByName.values().stream().map(Tool::toolSpecification).toList();
        try (var model = getActionProcessingModel()) {
            var prompt = TestCaseExecutionPlanPrompt.builder()
                    .withTestSteps(testSteps)
                    .withToolSpecs(toolSpecs)
                    .build();
            return model.generateAndGetResponseAsObject(prompt, TEST_CASE_EXECUTION_PLAN);
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
        return executeWithRetries(VERIFICATION_RETRY_TIMEOUT_MILLIS, VERIFICATION_EXECUTION, (model) -> {
            var screenshot = captureScreen();
            if(DEBUG_MODE){
                saveScreenshot(screenshot, "verification");
            }
            var prompt = VerificationExecutionPrompt.builder()
                    .withVerificationDescription(verification)
                    .withActionDescription(actionIncludingData)
                    .screenshot(screenshot)
                    .build();
            return model.generateAndGetResponseAsObject(prompt, VERIFICATION_EXECUTION);
        }, VerificationExecutionResult::success, Agent::getVerificationExecutionModel);
    }

    private static <T> T executeWithRetries(long retryTimeoutMillis, String operationDescription, Function<GenAiModel, T> resultSupplier,
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
                        waitTillNextRetry("The %s failed".formatted(operationDescription), deadline);
                    }
                }
            } while (retryActive);
        }
        return result;
    }

    private static VerificationExecutionResult processPreconditionValidationRequest(String precondition) {
        return executeWithRetries(VERIFICATION_RETRY_TIMEOUT_MILLIS, PRECONDITION_VALIDATION, (model) -> {
            var prompt = PreconditionVerificationPrompt.builder()
                    .withPreconditionDescription(precondition)
                    .screenshot(captureScreen())
                    .build();
            LOG.info("Checking if precondition is met: '{}'", precondition);
            return model.generateAndGetResponseAsObject(prompt, PRECONDITION_VALIDATION);
        }, VerificationExecutionResult::success, Agent::getVerificationExecutionModel);
    }

    private static void waitTillNextRetry(String retryCause, Instant deadline) {
        LOG.info("{}, retrying within configured deadline.", retryCause);
        var nextRetryMoment = getNextRetryMoment();
        if (nextRetryMoment.isBefore(deadline)) {
            waitUntil(nextRetryMoment);
        }
    }

    private static Instant getNextRetryMoment() {
        return now().plusMillis(TEST_STEP_RETRY_INTERVAL_MILLIS);
    }

    private static ToolExecutionResult executeRequestedTool(String toolName, List<String> args) {
        LOG.info("Model requested an execution of the tool '{}' with the following arguments map: <{}>", toolName, args);
        var tool = getTool(toolName);
        Class<?> toolClass = tool.clazz();
        int paramsAmount = tool.toolSpecification().parameters().properties().size();
        checkArgument(paramsAmount == args.size(), "Model requested '%s' tool with %s params, but this tool has only %s params.",
                toolName, paramsAmount, args.size());
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
            LOG.error("Exception thrown by tool '{}': {}", toolName, (ofNullable(e.getMessage()).orElse("Unknown Cause")), e);
            throw new RuntimeException("'%s' tool execution failed because of internal error.".formatted(toolName), e);
        }
    }

    private static Tool getTool(String toolName) {
        if (!allToolsByName.containsKey(toolName)) {
            throw new IllegalArgumentException(
                    "The requested tool '%s' is not registered, please fix the prompt".formatted(toolName));
        }
        return allToolsByName.get(toolName);
    }

    private static @NotNull Object[] parseArgumentsJson(String argsJson, Method method) {
        try {
            Map<String, Object> argumentsMap = OBJECT_MAPPER.readValue(argsJson, new TypeReference<>() {
            });
            return Arrays.stream(method.getParameters())
                    .map(parameter -> OBJECT_MAPPER.convertValue(argumentsMap.get(parameter.getName()), parameter.getType()))
                    .toArray();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Couldn't parse the tool arguments JSON: %s".formatted(argsJson), e);
        }
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
        return getInstructionModel(false);
    }

    private static GenAiModel getVerificationExecutionModel() {
        return getVisionModel(false);
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
