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

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.tarik.ta.user_dialogs.NoElementFoundPopup.UserDecision.CONTINUE;

public class NoElementFoundPopup extends AbstractDialog {
    public enum UserDecision {
        CONTINUE,
        TERMINATE
    }

    private final AtomicReference<UserDecision> userDecision = new AtomicReference<>(UserDecision.TERMINATE);

    private NoElementFoundPopup(String message) {
        super("UI element not found");
        var userMessageArea = getUserMessageArea(message);
        var continueButton = new JButton("Continue");
        continueButton.addActionListener(_ -> {
            userDecision.set(CONTINUE);
            dispose();
        });

        var terminateButton = new JButton("Terminate");
        terminateButton.addActionListener(_ -> dispose());

        JPanel buttonsPanel = getButtonsPanel(continueButton, terminateButton);
        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(new JScrollPane(userMessageArea), BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);

        setDefaultSizeAndPosition(0.2, 0.2);
        displayPopup();
    }

    @Override
    protected void onDialogClosing() {
        userDecision.set(UserDecision.TERMINATE);
    }

    public static UserDecision displayAndGetUserDecision(String message) {
        var popup = new NoElementFoundPopup(message);
        waitForUserInteractions(popup);
        return popup.userDecision.get();
    }
}