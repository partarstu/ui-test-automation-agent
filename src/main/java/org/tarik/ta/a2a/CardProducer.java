package org.tarik.ta.a2a;

 import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
 import org.tarik.ta.AgentConfig;

 import java.util.List;

public class CardProducer {
    private static final String AGENT_URL = String.format("http://%s:%d", AgentConfig.getHost(), AgentConfig.getStartPort());

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

