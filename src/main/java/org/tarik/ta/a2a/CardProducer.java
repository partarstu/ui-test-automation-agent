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

 import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
 import org.tarik.ta.AgentConfig;

 import java.util.List;

public class CardProducer {
    private static final String AGENT_URL = AgentConfig.getExternalUrl();

    public static AgentCard agentCard() {
        return new AgentCard.Builder()
                .name("UI Test Automation Agent")
                .description("Can execute UI tests in a fully automated mode")
                .url(AGENT_URL)
                .version("1.0.0")
                .capabilities(new AgentCapabilities.Builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .stateTransitionHistory(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .build();
    }
}

