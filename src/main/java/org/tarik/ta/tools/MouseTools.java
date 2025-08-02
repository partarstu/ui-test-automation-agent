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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.exceptions.UserChoseTerminationException;
import org.tarik.ta.exceptions.UserInterruptedExecutionException;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.function.Function;

import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.*;
import static org.tarik.ta.tools.ElementLocator.locateElementOnTheScreen;
import static org.tarik.ta.utils.CommonUtils.*;

public class MouseTools extends AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(MouseTools.class);
    private static final int MOUSE_ACTION_DELAY_MILLIS = 100;

    @Tool(value = "Performs a right click with a mouse at the specified UI element." +
            "Use this tool when you need to right-click on a specific UI element. " +
            "Provide a detailed description of the element, including its name, type, and any " +
            "relevant context that helps to identify it uniquely."
    )
    public static ToolExecutionResult rightMouseClick(
            @P(value = "Detailed description of the UI element to right-click on") String elementDescription) {
        if (elementDescription == null || elementDescription.isBlank()) {
            return getFailedToolExecutionResult("Can't click with right mouse button on an element without any description", true);
        }

        return executeUsingUiElement(elementDescription, elementLocation -> {
            robot.mouseMove(elementLocation.x, elementLocation.y);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            var message = "Clicked using right mouse button on '%s' using location %s"
                    .formatted(elementDescription, elementLocation);
            return getSuccessfulResult(message);
        });
    }

    @Tool(value = "Performs a left click with a mouse at the specified UI element." +
            "Use this tool when you need to left-click on a specific UI element. " +
            "Provide a detailed description of the element, including its name, type, and any " +
            "relevant context that helps to identify it uniquely."
    )
    public static ToolExecutionResult leftMouseClick(
            @P(value = "Detailed description of the UI element to left-click on") String elementDescription) {
        if (elementDescription == null || elementDescription.isBlank()) {
            return getFailedToolExecutionResult("Can't click an element without any description using mouse", true);
        }

        return executeUsingUiElement(elementDescription, elementLocation -> {
            robot.mouseMove(elementLocation.x, elementLocation.y);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            var message = "Clicked left mouse button on '%s' using location %s".formatted(elementDescription, elementLocation);
            return getSuccessfulResult(message);
        });
    }

    @Tool(value = "Performs a double click with a left mouse button at the specified UI element." +
            "Use this tool when you need to double-click on a specific UI element. " +
            "Provide a detailed description of the element, including its name, type, and any " +
            "relevant context that helps to identify it uniquely."
    )
    public static ToolExecutionResult leftMouseDoubleClick(
            @P(value = "Detailed description of the UI element to double-click on") String elementDescription) {
        if (elementDescription == null || elementDescription.isBlank()) {
            return getFailedToolExecutionResult("Can't double-click an element without any description using mouse", true);
        }

        return executeUsingUiElement(elementDescription, elementLocation -> {
            robot.mouseMove(elementLocation.x, elementLocation.y);
            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            var message =
                    "Double-clicked left mouse button on '%s' using location %s".formatted(elementDescription, elementLocation);
            return getSuccessfulResult(message);
        });
    }


    @Tool(value = "Moves the mouse to the center of the specified UI element. " +
            "Use this tool when you need to move the mouse to the center of a specific UI element. " +
            "Provide a detailed description of the element, including its name, type, and any " +
            "relevant context that helps to identify it uniquely."
    )
    public static ToolExecutionResult moveMouseToElementCenter(
            @P(value = "Detailed description of the UI element to move the mouse to") String elementDescription) {
        if (elementDescription == null || elementDescription.isBlank()) {
            return getFailedToolExecutionResult("Can't move mouse to an element without any description", true);
        }

        return executeUsingUiElement(elementDescription, elementLocation -> {
            robot.mouseMove(elementLocation.x, elementLocation.y);
            var message = "Moved mouse to the center of '%s' at location (%s, %s)"
                    .formatted(elementDescription, elementLocation.x, elementLocation.y);
            return getSuccessfulResult(message);
        });
    }

    @Tool(value = "Clicks and drags the mouse pointer from the current location by the specified amount of pixels." +
            "Use this when you need to click and drag the mouse pointer on the screen for the specific distance in any direction. " +
            "Provide the dragging distance in pixels on the X and Y scales."
    )
    public static ToolExecutionResult clickAndDrag(
            @P("Number of pixels to drag the mouse to the right (negative for left)") String xOffset,
            @P("Number of pixels to drag the mouse to the bottom (negative for up)") String yOffset) {
        return parseStringAsInteger(xOffset)
                .map(xOffsetInt -> parseStringAsInteger(yOffset)
                        .map(yOffsetInt -> {
                            var mouseLocation = getMouseLocation();
                            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
                            robot.mouseMove(mouseLocation.x + xOffsetInt, mouseLocation.y + yOffsetInt);
                            sleepMillis(MOUSE_ACTION_DELAY_MILLIS);
                            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                            var message = "Clicked and dragged the mouse from the point at %s by offset (%s, %s)"
                                    .formatted(mouseLocation, xOffset, yOffset);
                            return getSuccessfulResult(message);
                        })
                        .orElseGet(() -> getFailedToolExecutionResult("'%s' is not a valid integer value for the 'yOffset' variable"
                                .formatted(yOffset), true)))
                .orElseGet(() -> getFailedToolExecutionResult("'%s' is not a valid integer value for the 'xOffset' variable"
                        .formatted(xOffset), true));
    }

    @NotNull
    private static ToolExecutionResult executeUsingUiElement(String elementDescription,
                                                             Function<Point, ToolExecutionResult> executionResultProvider) {
        try {
            var point = locateElementOnTheScreen(elementDescription)
                    .map(boundingBox -> new Point((int) boundingBox.getCenterX(), (int) boundingBox.getCenterY()));
            return point.map(executionResultProvider)
                    .orElseGet(() -> getNoElementFoundResult(elementDescription));
        } catch (UserChoseTerminationException | UserInterruptedExecutionException e) {
            LOG.error(e.getMessage());
            return new ToolExecutionResult(INTERRUPTED_BY_USER, e.getMessage(), false);
        } catch (Exception e) {
            return getFailedToolExecutionResult(e.getMessage(), true);
        }
    }

    @NotNull
    private static ToolExecutionResult getNoElementFoundResult(String elementDescription) {
        return getFailedToolExecutionResult("The element with description '%s' was not found on the screen".formatted(elementDescription),
                true);
    }
}
