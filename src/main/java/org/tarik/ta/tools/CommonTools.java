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
package org.tarik.ta.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;

import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.ERROR;
import static org.tarik.ta.utils.CommonUtils.parseStringAsInteger;
import static org.tarik.ta.utils.CommonUtils.sleepSeconds;

public class CommonTools extends AbstractTools {
    @Tool(value = "Waits the specified amount of seconds. Use this tool when you need to wait after some action.")
    public static ToolExecutionResult waitSeconds(@P(value = "The specific amount of seconds to wait.") String secondsAmount) {
        return parseStringAsInteger(secondsAmount)
                .map(seconds -> {
                    sleepSeconds(seconds);
                    return getSuccessfulResult("Successfully waited for %s seconds".formatted(seconds));
                })
                .orElseGet(() -> new ToolExecutionResult(ERROR, "'%s' is not a valid integer value for the seconds to wait for."
                        .formatted(secondsAmount), true));
    }

    @Tool(value = "Opens the Chrome browser with the specified URL. Use this tool to navigate to a web page.")
    public static ToolExecutionResult openChromeBrowser(@P(value = "The URL to open in Chrome.") String url) {
        try {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start chrome"});
            return getSuccessfulResult("Successfully opened Chrome with URL: " + url);
        } catch (IOException e) {
            return getFailedToolExecutionResult("Failed to open Chrome browser: " + e.getMessage(), false);
        }
    }
}
