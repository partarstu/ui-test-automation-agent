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

import io.a2a.server.agentexecution.AgentExecutionResource;
import io.javalin.Javalin;
import io.javalin.json.JavalinGson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.javalin.Javalin.create;
import static org.tarik.ta.AgentConfig.getStartPort;

public class Server {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final long MAX_REQUEST_SIZE = 10000000;
    private static final String MAIN_PATH = "/";
    private static final String AGENT_CARD_PATH = "/.well-known/agent.json";
    private static final boolean UNATTENDED_MODE = AgentConfig.isUnattendedMode();

    public static void main(String[] args) {
        int port = getStartPort();
        AgentExecutionResource agentExecutionResource = new AgentExecutionResource();

        Javalin app = create(config -> {
            config.http.maxRequestSize = MAX_REQUEST_SIZE;
            config.jsonMapper(new JavalinGson());
        })
                .post(MAIN_PATH, ctx -> ctx.result(agentExecutionResource.handleNonStreamingRequests(ctx)))
                .get(AGENT_CARD_PATH, agentExecutionResource::getAgentCard)
                .start(port);

        LOG.info("Agent server started on port: {} in {} mode", port, UNATTENDED_MODE ? "unattended" : "attended");
    }
}