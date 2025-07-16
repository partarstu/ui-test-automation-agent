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
package org.tarik.ta.user_dialogs;

import org.tarik.ta.utils.CommonUtils;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

public class NextActionPopup extends AbstractDialog {

    public enum UserDecision {
        CREATE_NEW_ELEMENT,
        RETRY_SEARCH,
        TERMINATE
    }

    private static final String TITLE = "Further action required";
    private static final String DEFAULT_INPUT_MESSAGE = "What would you like to do next ?"; // New constant
    private final AtomicReference<UserDecision> userDecision = new AtomicReference<>(UserDecision.TERMINATE);

    private NextActionPopup(String message) {
        super(TITLE);
        var userMessageArea = getUserMessageArea(message);

        JButton createNewElementButton = new JButton("Create New Element");
        createNewElementButton.addActionListener(_ -> {
            userDecision.set(UserDecision.CREATE_NEW_ELEMENT);
            dispose();
        });

        JButton retrySearchButton = new JButton("Retry Search");
        retrySearchButton.addActionListener(_ -> {
            userDecision.set(UserDecision.RETRY_SEARCH);
            dispose();
        });

        JButton terminateButton = new JButton("Terminate");
        terminateButton.addActionListener(_ -> {
            userDecision.set(UserDecision.TERMINATE);
            dispose();
        });

        JPanel buttonsPanel = getButtonsPanel(createNewElementButton, retrySearchButton, terminateButton);
        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(new JScrollPane(userMessageArea), BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);

        setDefaultSizeAndPosition(0.3, 0.2);
        displayPopup();
    }

    @Override
    protected void onDialogClosing() {
        userDecision.set(UserDecision.TERMINATE);
    }

    public static UserDecision displayAndGetUserDecision(String message) {
        String actualMessage = CommonUtils.isNotBlank(message) ? message : DEFAULT_INPUT_MESSAGE;
        var popup = new NextActionPopup(actualMessage);
        waitForUserInteractions(popup);
        return popup.userDecision.get();
    }

    public static UserDecision displayAndGetUserDecision() {
        return displayAndGetUserDecision(DEFAULT_INPUT_MESSAGE);
    }
}