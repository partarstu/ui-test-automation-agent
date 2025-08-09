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
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URI;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.awt.Desktop.getDesktop;
import static java.awt.Desktop.isDesktopSupported;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.ERROR;
import static org.tarik.ta.utils.CommonUtils.*;

public class CommonTools extends AbstractTools {
    private static final int BROWSER_OPEN_TIME_SECONDS = 1;
    private static final Logger LOG = LoggerFactory.getLogger(CommonTools.class);
    private static final String HTTP_PROTOCOL = "http://";
    private static final String OS_NAME_SYS_PROPERTY = "os.name";
    private static final String HTTPS_PROTOCOL = "https://";
    private static Process browserProcess;

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

    @Tool(value = "Opens the default browser with the specified URL. Use this tool to navigate to a web page.")
    public static synchronized ToolExecutionResult openBrowser(@P(value = "The URL to open in the browser.") String url) {
        if (isBlank(url)) {
            return getFailedToolExecutionResult("URL must be provided", true);
        }

        try {
            closeBrowser(); // Close any existing browser instance

            if (!url.toLowerCase().startsWith(HTTP_PROTOCOL) && !url.toLowerCase().startsWith(HTTPS_PROTOCOL)) {
                LOG.warn("Provided URL '{}' doesn't have the protocol defined, using HTTP as the default one", url);
                url = HTTP_PROTOCOL + url;
            }
            if (isDesktopSupported() && getDesktop().isSupported(Desktop.Action.BROWSE)) {
                getDesktop().browse(new URI(url));
            } else {
                LOG.debug("Java AWT Desktop is not supported on the current OS, falling back to alternative method.");
                String os = System.getProperty(OS_NAME_SYS_PROPERTY).toLowerCase();
                if (isBlank(url)) {
                    return getFailedToolExecutionResult("The type of the current OS can't be identified using '%s' system property, " +
                            "can't proceed without it. and URL is blank", true);
                }
                String[] command = buildBrowserStartupCommand(os, url);
                LOG.debug("Executing command: {}", String.join(" ", command));
                browserProcess = new ProcessBuilder(command).start();
                if (!browserProcess.isAlive()) {
                    var errorMessage = "Failed to open browser. Error: %s\n"
                            .formatted(IOUtils.toString(browserProcess.getErrorStream(), UTF_8));
                    return getFailedToolExecutionResult(errorMessage, false);
                }
            }
            sleepSeconds(BROWSER_OPEN_TIME_SECONDS);
            return getSuccessfulResult("Successfully opened default browser with URL: " + url);
        } catch (Exception e) {
            return getFailedToolExecutionResult("Failed to open default browser: " + e.getMessage(), false);
        }
    }

    @Tool(value = "Closes the currently open browser instance. Use this tool when you need to close the browser.")
    public static synchronized ToolExecutionResult closeBrowser() {
        if (browserProcess != null && browserProcess.isAlive()) {
            browserProcess.destroy();
            try {
                browserProcess.waitFor(); // Wait for the process to terminate
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return getFailedToolExecutionResult("Failed to close browser: " + e.getMessage(), false);
            }
            return getSuccessfulResult("Browser closed successfully.");
        } else {
            return getSuccessfulResult("No active browser process to close.");
        }
    }

    private static String[] buildBrowserStartupCommand(String os, String url) {
        if (os.contains("win")) {
            return new String[]{"cmd.exe", "/c", "start", url};
        } else if (os.contains("mac")) {
            return new String[]{"open", url};
        } else {
            String browserCommand = System.getenv("BROWSER_COMMAND");
            if (browserCommand == null || browserCommand.trim().isEmpty()) {
                browserCommand = "chromium-browser";
            }
            return new String[]{browserCommand, "--no-sandbox", "--start-maximized", url};
        }
    }
}
