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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.ERROR;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.SUCCESS;
import static org.tarik.ta.utils.CommonUtils.getRobot;

public class AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTools.class);
    protected static final Robot robot = getRobot();

    public enum ToolExecutionStatus {
        SUCCESS, ERROR, INTERRUPTED_BY_USER
    }

    @NotNull
    protected static ToolExecutionResult getSuccessfulResult(String message) {
        LOG.info(message);
        return new ToolExecutionResult(SUCCESS, message, false);
    }

    @NotNull
    protected static ToolExecutionResult getFailedToolExecutionResult(String message, boolean retryMakesSense) {
        LOG.error(message);
        return new ToolExecutionResult(ERROR, message, retryMakesSense);
    }

    public record ToolExecutionResult(ToolExecutionStatus executionStatus, String message, boolean retryMakesSense) {
    }
}