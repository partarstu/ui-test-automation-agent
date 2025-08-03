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
package org.tarik.ta.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.requesthandlers.JSONRPCHandler;
import io.a2a.server.tasks.InMemoryPushNotifier;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.spec.*;
import io.a2a.spec.InternalError;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;

public class AgentExecutionResource {
    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutionResource.class);
    private final DefaultRequestHandler httpRequestHandler = new DefaultRequestHandler(new UiAgentExecutor(),
            new InMemoryTaskStore(), new InMemoryQueueManager(), new InMemoryPushNotifier(), Executors.newSingleThreadExecutor());
    private final JSONRPCHandler jsonRpcHandler = new JSONRPCHandler(CardProducer.agentCard(), httpRequestHandler);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * Handles incoming POST requests to the main A2A endpoint.
     *
     * @return the JSON-RPC response which may be an error response
     */
    public String handleNonStreamingRequests(@NotNull Context context) {
        JSONRPCResponse<?> response;
        try {
            var body = context.body();
            var request = objectMapper.readValue(body, java.util.Map.class);
            var method = (String) request.get("method");
            if (method == null) {
                response = new JSONRPCErrorResponse(null, new UnsupportedOperationError());
            } else {
                response = switch (method) {
                    case GetTaskRequest.METHOD -> jsonRpcHandler.onGetTask(objectMapper.readValue(body, GetTaskRequest.class));
                    case CancelTaskRequest.METHOD -> jsonRpcHandler.onCancelTask(objectMapper.readValue(body, CancelTaskRequest.class));
                    case SetTaskPushNotificationConfigRequest.METHOD ->
                            jsonRpcHandler.setPushNotification(objectMapper.readValue(body, SetTaskPushNotificationConfigRequest.class));
                    case GetTaskPushNotificationConfigRequest.METHOD ->
                            jsonRpcHandler.getPushNotification(objectMapper.readValue(body, GetTaskPushNotificationConfigRequest.class));
                    case SendMessageRequest.METHOD -> jsonRpcHandler.onMessageSend(objectMapper.readValue(body, SendMessageRequest.class));
                    default -> new JSONRPCErrorResponse(null, new UnsupportedOperationError());
                };
            }

            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            try {
                LOG.error("Got error while processing agent task request", e);
                return objectMapper.writeValueAsString(new JSONRPCErrorResponse(null, new InternalError(e.getMessage())));
            } catch (Exception ex) {
                return "{}";
            }
        }
    }

    public void getAgentCard(@NotNull Context context) {
        context.json(jsonRpcHandler.getAgentCard());
    }
}