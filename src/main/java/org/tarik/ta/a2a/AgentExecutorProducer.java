/*
 * Copyright (c) 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.tarik.ta.a2a;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.tarik.ta.Agent;

import java.util.List;

@ApplicationScoped
public class AgentExecutorProducer {
    @Inject
    Agent agent;

    @Produces
    public UiAgentExecutor agentExecutor() {
        return new UiAgentExecutor(agent);
    }

    private record UiAgentExecutor(Agent agent) implements AgentExecutor {

        @Override
        public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
            TaskUpdater updater = new TaskUpdater(context, eventQueue);

            // mark the task as submitted and start working on it
            if (context.getTask() == null) {
                updater.submit();
            }
            updater.startWork();

            // extract the text from the message
            String userMessage = extractTextFromMessage(context.getMessage());

            // call the weather agent with the user's message

            String response = "";//agent.chat(userMessage);

            // create the response part
            TextPart responsePart = new TextPart(response, null);
            List<Part<?>> parts = List.of(responsePart);

            // add the response as an artifact and complete the task
            updater.addArtifact(parts, null, null, null);
            updater.complete();
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

        private String extractTextFromMessage(Message message) {
            StringBuilder textBuilder = new StringBuilder();
            if (message.getParts() != null) {
                for (Part part : message.getParts()) {
                    if (part instanceof TextPart textPart) {
                        textBuilder.append(textPart.getText());
                    }
                }
            }
            return textBuilder.toString();
        }
    }
}