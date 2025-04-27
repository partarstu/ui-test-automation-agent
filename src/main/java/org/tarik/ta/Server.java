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

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinGson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.helper_entities.TestCase;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import static io.javalin.Javalin.create;
import static io.javalin.http.HttpStatus.*;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.tarik.ta.AgentConfig.getStartPort;

public class Server {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final Semaphore executionSemaphore = new Semaphore(1);
    private static final long MAX_REQUEST_SIZE = 10000000;
    private static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final String MAIN_PATH = "/testcase";

    public static void main(String[] args) {
        int port = getStartPort();
        Javalin app = create(config -> {
            config.http.maxRequestSize = MAX_REQUEST_SIZE;
            config.jsonMapper(new JavalinGson());
        })
                .start(port);

        LOG.info("Agent server started on port: {}", port);

        app.post(MAIN_PATH, ctx -> {
            LOG.info("Received test case execution request.");
            if (executionSemaphore.tryAcquire()) {
                LOG.info("Thread lock acquired by request handler.");
                try {
                    parseTestCaseFromRequest(ctx).ifPresentOrElse(requestedTestCase -> executorService.submit(() -> {
                                String testCaseName = requestedTestCase.name();
                                try {
                                    LOG.info("Starting execution of the test case '{}'", testCaseName);
                                    Agent.executeTestCase(requestedTestCase);
                                    LOG.info("Finished execution of the test case '{}'", testCaseName);
                                } catch (Exception e) {
                                    LOG.error("Got exception during the execution of the test case '{}'", testCaseName, e);
                                    ctx.status(INTERNAL_SERVER_ERROR).result("Internal server error: " + e.getMessage());
                                } finally {
                                    executionSemaphore.release();
                                    LOG.info("Thread lock released by background thread for TestCase: {}", testCaseName);
                                }
                            }),
                            () -> {
                                var message = "Request contains no valid test case.";
                                LOG.error(message);
                                ctx.status(BAD_REQUEST).result(message);
                                executionSemaphore.release();
                                LOG.info("Thread lock released by request handler because test case parsing failed.");
                            });

                    ctx.status(OK).result("Test case execution started.");
                } catch (Exception e) {
                    LOG.error("Error processing test case execution request", e);
                    ctx.status(INTERNAL_SERVER_ERROR).result("Internal server error: " + e.getMessage());
                    executionSemaphore.release();
                    LOG.info("Thread lock released due to error during request processing.");
                }
            } else {
                LOG.warn("Agent already running, rejecting request.");
                ctx.status(TOO_MANY_REQUESTS).result("Agent is already processing another test case.");
            }
        });
    }

    private static Optional<TestCase> parseTestCaseFromRequest(Context ctx) {
        try {
            var requestedTestCase = ctx.bodyAsClass(TestCase.class);
            if (requestedTestCase == null || requestedTestCase.name() == null || requestedTestCase.testSteps() == null) {
                LOG.error("Invalid TestCase JSON received.");
                return empty();
            }
            LOG.info("Deserialized TestCase: {}", requestedTestCase.name());
            return of(requestedTestCase);
        } catch (Exception e) {
            LOG.error("Failed to deserialize TestCase JSON", e);
            return empty();
        }
    }
}