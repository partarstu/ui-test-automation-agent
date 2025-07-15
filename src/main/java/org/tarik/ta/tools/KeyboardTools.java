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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.awt.Toolkit.getDefaultToolkit;
import static java.awt.event.KeyEvent.*;
import static java.lang.Character.isUpperCase;
import static java.util.Arrays.stream;
import static java.util.stream.IntStream.range;
import static org.tarik.ta.tools.AbstractTools.ToolExecutionStatus.*;
import static org.tarik.ta.tools.MouseTools.leftMouseClick;
import static org.tarik.ta.utils.CommonUtils.*;

public class KeyboardTools extends AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(KeyboardTools.class);
    private static final Map<String, Integer> actionableKeyCodeByNameMap = getActionableKeyCodesByName();
    private static final int MAX_KEY_INDEX = 120000;
    private static final int KEYBOARD_ACTION_DELAY_MILLIS = 500;
    private static final int AUTO_DELAY = 30;

    @Tool(value = "Presses the specified keyboard key. Use this tool when you need to press a single keyboard key.")
    public static ToolExecutionResult pressKey(@P(value = "The specific value of a keyboard key which needs to be pressed, e.g. 'Ctrl', " +
            "'Enter', 'A', '1', 'Shift' etc.") String keyboardKey) {
        robot.setAutoDelay(AUTO_DELAY);
        if (keyboardKey == null || keyboardKey.isBlank()) {
            return getFailedToolExecutionResult("%s: In order to press a keyboard key it can't be empty"
                    .formatted(KeyboardTools.class.getSimpleName()), true);
        }
        int keyCode = getKeyCode(keyboardKey);
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
        return getSuccessfulResult("Pressed '%s' key".formatted(keyboardKey));
    }

    @Tool(value = "Presses the specified sequence of keyboard keys. Use this tool when you need to press a combination or sequence of" +
            " multiple keyboard keys at the same time."
    )
    public static ToolExecutionResult pressKeys(@P("A non-empty array of values each representing the keyboard key which needs to be " +
            "pressed, e.g. 'Ctrl', 'Enter', 'A', '1', 'Shift' etc.") String... keyboardKeys) {
        robot.setAutoDelay(AUTO_DELAY);
        if (keyboardKeys == null || keyboardKeys.length == 0) {
            return getFailedToolExecutionResult("%s: In order to press keyboard keys combination it can't be empty"
                    .formatted(KeyboardTools.class.getSimpleName()), true);
        }
        stream(keyboardKeys).map(KeyboardTools::getKeyCode).forEach(robot::keyPress);
        stream(keyboardKeys).map(KeyboardTools::getKeyCode).forEach(robot::keyRelease);
        var message = "Pressed the following keys combination: '%s'".formatted(String.join(" + ", keyboardKeys));
        return getSuccessfulResult(message);
    }

    @Tool(value = "Types (enters, inputs) the specified text using the keyboard. If the text needs to be input into the element which " +
            "needs to be explicitly activated, this element is first clicked with a left mouse key and only then the text is input - " +
            "a detailed description of such element needs to be provided in this case. If the contents of the element, into which the " +
            "text needs to be input, are not empty, they could be wiped out before input - the corresponding boolean " +
            "parameter must be set in this case.")
    public static ToolExecutionResult typeText(
            @P(value = "The text to be typed.")
            String text,
            @P(value = "Detailed description of the UI element in which the text should be input.", required = false)
            String elementDescription,
            @P(value = "A boolean which defines if existing contents of the UI element, in which the text should be input, need to be " +
                    "wiped out before input", required = false)
            String wipeOutOldContent) {
        robot.setAutoDelay(AUTO_DELAY);
        if (text == null) {
            return getFailedToolExecutionResult("%s: Text which needs to be input can't be NULL"
                    .formatted(KeyboardTools.class.getSimpleName()), true);
        }

        if (isNotBlank(wipeOutOldContent) && !List.of("true", "false").contains(wipeOutOldContent.trim().toLowerCase())) {
            return getFailedToolExecutionResult(("%s: Got incorrect value for the variable which defines if the content should be wiped " +
                    "out. Expected boolean value, got : {%s}")
                    .formatted(KeyboardTools.class.getSimpleName(), wipeOutOldContent), true);
        }

        if (isNotBlank(elementDescription)) {
            var mouseResult = leftMouseClick(elementDescription);
            if (mouseResult.executionStatus() != SUCCESS) {
                return mouseResult;
            }
        }

        if (isBlank(wipeOutOldContent) || Boolean.parseBoolean(wipeOutOldContent)) {
            selectAndDeleteContent();
        }

        for (char ch : text.toCharArray()) {
            try {
                typeCharacter(ch);
            } catch (Exception e) {
                LOG.info("Couldn't type '{}' character using keyboard keys, falling back to copy-paste.", ch);
                // Fallback option - copy-paste using clipboard
                try {
                    getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(String.valueOf(ch)), null);
                    robot.keyPress(VK_CONTROL);
                    robot.keyPress(VK_V);
                    robot.keyRelease(VK_CONTROL);
                    robot.keyRelease(VK_V);
                } catch (Exception ex) {
                    String message = "Got error while copy-pasting '%s' character.".formatted(ch);
                    LOG.error(message, e);
                    return getFailedToolExecutionResult("Couldn't press the key '%s', original error: %s".formatted(ch, e), true);
                }
            }
        }
        return getSuccessfulResult("Input the following text using keyboard: %s".formatted(text));
    }

    @Tool(value = "Clears (wipes out) data inside the specified input field.")
    public static ToolExecutionResult clearData(
            @P(value = "Detailed description of the UI element which needs to have the content cleared.")
            String elementDescription) {
        robot.setAutoDelay(AUTO_DELAY);
        if (isBlank(elementDescription)) {
            return new ToolExecutionResult(ERROR, "%s: Can't clear the contents of an element without any description"
                    .formatted(MouseTools.class.getSimpleName()), true);
        }

        var mouseResult = leftMouseClick(elementDescription);
        if (mouseResult.executionStatus() != SUCCESS) {
            return mouseResult;
        }
        selectAndDeleteContent();
        return getSuccessfulResult("Cleared the contents of %s".formatted(elementDescription));
    }


    private static void selectAndDeleteContent() {
        robot.keyPress(VK_CONTROL);
        robot.keyPress(VK_A);
        robot.keyRelease(VK_CONTROL);
        robot.keyRelease(VK_A);
        robot.keyPress(VK_BACK_SPACE);
        robot.keyRelease(VK_BACK_SPACE);
        sleepMillis(KEYBOARD_ACTION_DELAY_MILLIS);
    }

    private static void typeCharacter(char ch) {
        int keyCode = getKeyCode(ch).orElseGet(() -> getKeyCode(String.valueOf(ch)));
        if (isUpperCase(ch)) {
            robot.keyPress(VK_SHIFT);
        }
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
        if (isUpperCase(ch)) {
            robot.keyRelease(VK_SHIFT);
        }
    }

    private static int getKeyCode(String keyboardKeyName) {
        if (!actionableKeyCodeByNameMap.containsKey(keyboardKeyName.toLowerCase())) {
            throw new IllegalArgumentException("There is no keyboard key with the name '%s'".formatted(keyboardKeyName));
        }
        return actionableKeyCodeByNameMap.get(keyboardKeyName.toLowerCase());
    }

    private static Map<String, Integer> getActionableKeyCodesByName() {
        Map<String, Integer> result = new HashMap<>();
        range(0, MAX_KEY_INDEX)
                .filter(ind -> !getKeyText(ind).toLowerCase().contains("unknown"))
                .filter(ind -> ind != VK_UNDEFINED)
                .forEach(ind -> result.put(getKeyText(ind), ind));
        return result;
    }

    private static Optional<Integer> getKeyCode(char c) {
        return Optional.of(KeyEvent.getExtendedKeyCodeForChar(c)).filter(code -> code != VK_UNDEFINED);
    }
}
