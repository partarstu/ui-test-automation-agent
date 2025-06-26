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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.enterprise.context.ApplicationScoped;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.dto.VerificationExecutionResult;
import org.tarik.ta.exceptions.ActionExecutionException;
import org.tarik.ta.exceptions.VerificationExecutionException;
import org.tarik.ta.helper_entities.ActionExecutionResult;
import org.tarik.ta.helper_entities.TestCase;
import org.tarik.ta.helper_entities.TestStep;
import org.tarik.ta.model.GenAiModel;
import org.tarik.ta.prompts.ActionExecutionPrompt;
import org.tarik.ta.prompts.VerificationExecutionPrompt;
import org.tarik.ta.tools.AbstractTools.ToolExecutionResult;
import org.tarik.ta.tools.AbstractTools.ToolExecutionStatus;
import org.tarik.ta.tools.CommonTools;
import org.tarik.ta.tools.KeyboardTools;
import org.tarik.ta.tools.MouseTools;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom;
import static java.lang.System.exit;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static java.util.stream.IntStream.range;
import static org.tarik.ta.AgentConfig.*;
import static org.tarik.ta.model.ModelFactory.getInstructionModel;
import static org.tarik.ta.model.ModelFactory.getVisionModel;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.*;
import static org.tarik.ta.utils.CommonUtils.*;

@ApplicationScoped
public class Agent {
    private static final Logger LOG = LoggerFactory.getLogger(Agent.class);
    protected static final Gson GSON = new Gson();
    protected static final Map<String, Tool> allToolsByName = getToolsByName();
    protected static final int TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS = getTestStepExecutionRetryTimeoutMillis();
    protected static final int VERIFICATION_RETRY_TIMEOUT_MILLIS = getVerificationRetryTimeoutMillis();
    protected static final int TEST_STEP_RETRY_INTERVAL_MILLIS = getTestStepExecutionRetryIntervalMillis();
    protected static final int ACTION_VERIFICATION_DELAY_MILLIS = getActionVerificationDelayMillis();
    private static final int ERROR_STATUS = 1;
    private static final String ACTION_EXECUTION = "action execution";
    private static final String VERIFICATION_EXECUTION = "verification execution";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Arguments missing: path to the file which contains JSON");
            exit(ERROR_STATUS);
        }
        String useCaseJsonPath = args[0];
        deserializeJsonFromFile(useCaseJsonPath, TestCase.class).ifPresentOrElse(testCase -> {
            LOG.info("Starting Agent execution for test case: {}", testCase.name());
            try {
                executeTestCase(testCase);
                LOG.info("Finished Agent execution successfully for test case: {}", testCase.name());
            } catch (Exception e) {
                LOG.error("Agent execution failed for test case: {}", testCase.name(), e);
                throw e;
            }
        }, () -> {
            var message = "Failed to load test case from: " + useCaseJsonPath;
            LOG.error(message);
            exit(ERROR_STATUS);
        });
    }

    public static void executeTestCase(TestCase testCase) throws IllegalStateException {
        for (TestStep testStep : testCase.testSteps()) {
            var actionInstruction = testStep.stepDescription();
            var verificationInstruction = testStep.expectedResults();
            var testData = testStep.testData();

            String actionInstructionWithData = actionInstruction;
            if (testData != null && !testData.isEmpty()) {
                actionInstructionWithData += " using following input data: '%s'".formatted(String.join("', '", testData));
            }

            var actionResult = processActionRequest(actionInstructionWithData);
            if (!actionResult.success()) {
                throw new ActionExecutionException(actionInstructionWithData, actionResult);
            }

            if (isNotBlank(verificationInstruction)) {
                sleepMillis(ACTION_VERIFICATION_DELAY_MILLIS);
                var finalInstruction = "Verify that %s".formatted(verificationInstruction);
                var verificationResult = processVerificationRequest(finalInstruction);
                if (!verificationResult.success()) {
                    throw new VerificationExecutionException(verificationInstruction, verificationResult);
                }
            }
        }
    }

    private static ActionExecutionResult processActionRequest(String action) {
        var prompt = ActionExecutionPrompt.builder().withActionDescription(action).build();
        var deadline = now().plusMillis(TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS);
        LOG.info("Executing action '{}'", action);
        var toolSpecs = allToolsByName.values().stream().map(Tool::toolSpecification).toList();
        try (var model = getActionProcessingModel()) {
            return executeActionRequest(model, prompt, toolSpecs, deadline);
        }
    }

    @Nullable
    private static ActionExecutionResult executeActionRequest(GenAiModel model, ActionExecutionPrompt prompt,
                                                              List<ToolSpecification> toolSpecs, Instant deadline) {
        var message = model.generate(prompt, toolSpecs, ACTION_EXECUTION).aiMessage();
        if (message.hasToolExecutionRequests()) {
            var toolExecutionRequests = message.toolExecutionRequests();
            checkState(toolExecutionRequests != null && !toolExecutionRequests.isEmpty(),
                    "Tools execution requests are empty, but were requested");
            checkState(toolExecutionRequests.size() == 1,
                    "Currently the execution only of a single tool is supported, but %s were requested", toolExecutionRequests.size());
            try {
                var toolSpecToExecute = toolExecutionRequests.getFirst();
                var nextRetryMoment = getNextRetryMoment();
                var toolExecutionResult = executeRequestedTool(toolSpecToExecute);
                return switch (toolExecutionResult) {
                    case ToolExecutionResult(ToolExecutionStatus status, String info, boolean _) when status == SUCCESS ->
                            new ActionExecutionResult(true, info);
                    case ToolExecutionResult(ToolExecutionStatus _, String info, boolean retryMakesSense) when !retryMakesSense ->
                            new ActionExecutionResult(false, info);
                    case ToolExecutionResult(ToolExecutionStatus _, String _, boolean _) when now().isBefore(deadline) -> {
                        if (nextRetryMoment.isBefore(deadline)) {
                            waitUntil(getNextRetryMoment());
                        }
                        LOG.info("Tool execution wasn't successful, retrying.");
                        yield executeActionRequest(model, prompt, toolSpecs, deadline);
                    }
                    case ToolExecutionResult(ToolExecutionStatus _, String info, _) -> {
                        LOG.info("Tool execution retries exhausted, interrupting the execution.");
                        yield new ActionExecutionResult(false, info);
                    }
                };
            } catch (Exception e) {
                LOG.error("Got exception while invoking requested tools:", e);
                return new ActionExecutionResult(false, e.getLocalizedMessage());
            }
        } else {
            return new ActionExecutionResult(false,
                    "Tools were not used but any action execution requires tools. Model response: " + message.text());
        }
    }

    private static VerificationExecutionResult processVerificationRequest(String verification) {
        var prompt = VerificationExecutionPrompt.builder()
                .withVerificationDescription(verification)
                .screenshot(captureScreen())
                .build();
        var deadline = now().plusMillis(VERIFICATION_RETRY_TIMEOUT_MILLIS);
        VerificationExecutionResult result;
        boolean retryActive;
        try (var model = getVerificationExecutionModel()) {
            do {
                LOG.info("'{}'", verification);
                result = model.generateAndGetResponseAsObject(prompt, VERIFICATION_EXECUTION);
                LOG.info("Result of verification '{}' : <{}>", verification, result);
                if (result.success()) {
                    return result;
                } else {
                    retryActive = now().isBefore(deadline);
                    if (retryActive) {
                        LOG.info("Verification failed, retrying within configured deadline.");
                        var nextRetryMoment = getNextRetryMoment();
                        if (nextRetryMoment.isBefore(deadline)) {
                            waitUntil(nextRetryMoment);
                        }
                    }
                }
            } while (retryActive);
        }

        return result;
    }

    private static Instant getNextRetryMoment() {
        return now().plusMillis(TEST_STEP_RETRY_INTERVAL_MILLIS);
    }

    private static ToolExecutionResult executeRequestedTool(ToolExecutionRequest toolExecutionRequest) {
        var toolName = toolExecutionRequest.name();
        String args = toolExecutionRequest.arguments();
        LOG.info("Model requested an execution of the tool '{}' with the following arguments map: <{}>", toolName, args);
        if (!allToolsByName.containsKey(toolName)) {
            throw new IllegalArgumentException(
                    "The requested tool '%s' is not registered, please fix the prompt".formatted(toolName));
        }
        var tool = allToolsByName.get(toolName);
        Class<?> toolClass = tool.clazz();
        int paramsAmount = tool.toolSpecification().parameters().properties().size();
        var method = getToolClassMethod(toolClass, toolName, paramsAmount);
        Map<String, String> argumentsMap = GSON.fromJson(args, new TypeToken<Map<String, String>>() {
        }.getType());
        var arguments = Arrays.stream(method.getParameters())
                .map(Parameter::getName)
                .map(paramName -> argumentsMap.getOrDefault(paramName, null))
                .toArray(String[]::new);
        try {
            var result = (ToolExecutionResult) method.invoke(toolClass, (Object[]) arguments);
            LOG.info("Tool execution completed '{}' using arguments: <{}>", toolName, Arrays.toString(arguments));
            return result;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Access denied while invoking tool '%s'".formatted(toolName), e);
        } catch (InvocationTargetException e) {
            var toolError = e.getCause();
            LOG.error("Exception thrown by tool '{}': {}", toolName, toolError != null ? toolError.getMessage() : "Unknown Cause",
                    toolError);
            throw new RuntimeException("'%s' tool execution failed. The cause: %s".formatted(toolName,
                    ofNullable(e.getMessage()).or(() -> ofNullable(e.getCause().getMessage())).orElse("Unknown")), toolError);
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
        return getInstructionModel();
    }

    private static GenAiModel getVerificationExecutionModel() {
        return getVisionModel();
    }

    private static Map<String, Tool> getToolsByName() {
        return Stream.of(KeyboardTools.class, MouseTools.class, CommonTools.class)
                .map(Agent::getTools)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(Tool::name, Function.identity()));
    }

    protected record Tool(String name, ToolSpecification toolSpecification, Class<?> clazz) {
    }
}