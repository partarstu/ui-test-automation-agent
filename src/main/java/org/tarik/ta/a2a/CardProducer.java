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

import io.a2a.server.PublicAgentCard;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class CardProducer {
    @ConfigProperty(name = "quarkus.http.host", defaultValue = "0.0.0.0")
    String host;
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int port;

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        String effectiveHost = "0.0.0.0".equals(host) ? "localhost" : host;
        String agentUrl = String.format("http://%s:%d", effectiveHost, port);

        return new AgentCard.Builder()
                .name("UI Test Automation Agent")
                .description("Can execute UI tests in a fully automated mode")
                .url(agentUrl)
                .version("1.0.0")
                .capabilities(new AgentCapabilities.Builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .stateTransitionHistory(false)
                        .build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(List.of())
                .build();
    }
}

