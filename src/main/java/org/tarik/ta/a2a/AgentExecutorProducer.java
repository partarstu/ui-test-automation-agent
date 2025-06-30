package org.tarik.ta.a2a;

import com.google.gson.Gson;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.Agent;
import org.tarik.ta.dto.TestExecutionResult;
import org.tarik.ta.helper_entities.TestCase;
import org.tarik.ta.prompts.TestCaseExtractionPrompt;
import org.tarik.ta.utils.CommonUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Optional.*;
import static java.util.stream.Collectors.joining;
import static org.tarik.ta.Agent.executeTestCase;
import static org.tarik.ta.model.ModelFactory.getInstructionModel;
import static org.tarik.ta.utils.CommonUtils.isBlank;
import static org.tarik.ta.utils.ImageUtils.convertImageToBase64;

@ApplicationScoped
public class AgentExecutorProducer {
    @Inject
    Agent agent;

    @Produces
    public UiAgentExecutor agentExecutor() {
        return new UiAgentExecutor(agent);
    }

    private record UiAgentExecutor(Agent agent) implements AgentExecutor {
        private static final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
        private static final Logger LOG = LoggerFactory.getLogger(UiAgentExecutor.class);
        private static final Gson GSON = new Gson();


        @Override
        public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
            TaskUpdater updater = new TaskUpdater(context, eventQueue);
            if (context.getTask() == null) {
                updater.submit();
            }

            LOG.info("Received test case execution request. Submitting to the execution queue.");
            taskExecutor.submit(() -> {
                var taskId = context.getTaskId();
                LOG.info("Starting task {} from the queue.", taskId);
                try {
                    updater.startWork();
                    extractTextFromMessage(context.getMessage()).ifPresentOrElse(userMessage ->
                                    parseTestCaseFromRequest(userMessage).ifPresentOrElse(requestedTestCase ->
                                                    requestTestCaseExecution(requestedTestCase, updater),
                                            () -> {
                                                var message = "Request for test case execution either contained no valid test case or " +
                                                        "insufficient information in order to execute it.";
                                                LOG.error(message);
                                                failTask(updater, message);
                                            }),
                            () -> {
                                var message = "Request for test case execution was empty.";
                                LOG.error(message);
                                failTask(updater, message);
                            });
                } catch (Exception e) {
                    LOG.error("Error while processing test case execution request for task {}", taskId, e);
                    failTask(updater, "Couldn't start the task %s".formatted(taskId));
                }
            });
        }

        private void requestTestCaseExecution(TestCase requestedTestCase, TaskUpdater updater) {
            String testCaseName = requestedTestCase.name();
            try {
                LOG.info("Starting execution of the test case '{}'", testCaseName);
                var result = executeTestCase(requestedTestCase);
                LOG.info("Finished execution of the test case '{}'", testCaseName);
                List<Part<?>> parts = new LinkedList<>();
                TextPart textPart = new TextPart(GSON.toJson(result), null);
                parts.add(textPart);
                addScreenshots(result, parts);
                updater.addArtifact(parts, null, null, null);
                updater.complete();
            } catch (Exception e) {
                LOG.error("Got exception during the execution of the test case '{}'", testCaseName, e);
                failTask(updater, "Got exception while executing the test case. " +
                        "Before re-sending please investigate the root cause based on the agent's logs.");
            }
        }

        private static void addScreenshots(TestExecutionResult result, List<Part<?>> parts) {
            result.stepResults().stream()
                    .filter(r -> r.getScreenshot() != null)
                    .map(r -> new FileWithBytes(
                            "image/png",
                            "Screenshot for the test step %s".formatted(r.getTestStep().stepDescription()),
                            convertImageToBase64(r.getScreenshot(), "png"))
                    )
                    .map(FilePart::new)
                    .forEach(parts::add);
        }

        private static void failTask(TaskUpdater updater, String message) {
            TextPart errorPart = new TextPart(message, null);
            List<Part<?>> parts = List.of(errorPart);
            updater.fail(updater.newAgentMessage(parts, null));
        }

        @Override
        public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
            Task task = context.getTask();

            if (task.getStatus().state() == TaskState.CANCELED) {
                throw new TaskNotCancelableError();
            }

            if (task.getStatus().state() == TaskState.COMPLETED) {
                throw new TaskNotCancelableError();
            }

            TaskUpdater updater = new TaskUpdater(context, eventQueue);
            updater.cancel();
        }

        private Optional<String> extractTextFromMessage(Message message) {
            String result = ofNullable(message.getParts())
                    .stream()
                    .flatMap(Collection::stream)
                    .filter(p->p instanceof TextPart)
                    .map(part -> ((TextPart) part).getText())
                    .filter(CommonUtils::isNotBlank)
                    .map(String::trim)
                    .collect(joining("\n"))
                    .trim();
            return result.isBlank() ? empty() : of(result);
        }

        private static Optional<TestCase> parseTestCaseFromRequest(String message) {
            LOG.info("Attempting to extract TestCase instance from user message using AI model.");
            if (isBlank(message)) {
                LOG.error("User message is blank, cannot extract a TestCase.");
                return empty();
            }

            try (var model = getInstructionModel()) {
                var prompt = TestCaseExtractionPrompt.builder()
                        .withUserRequest(message)
                        .build();
                TestCase extractedTestCase = model.generateAndGetResponseAsObject(prompt, "test case extraction");
                if (extractedTestCase == null || isBlank(extractedTestCase.name()) || extractedTestCase.testSteps() == null ||
                        extractedTestCase.testSteps().isEmpty()) {
                    LOG.warn("Model could not extract a valid TestCase from the provided by the user message, original message: {}",
                            message);
                    return empty();
                } else {
                    LOG.info("Successfully extracted TestCase: '{}'", extractedTestCase.name());
                    return of(extractedTestCase);
                }
            } catch (Exception e) {
                LOG.error("Failed to extract TestCase from user message due to an exception.", e);
                return empty();
            }
        }
    }
}