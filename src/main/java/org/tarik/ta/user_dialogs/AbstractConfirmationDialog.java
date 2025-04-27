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

public abstract class AbstractConfirmationDialog extends AbstractDialog {
    public AbstractConfirmationDialog(String title) throws HeadlessException {
        super(title);
    }

    @Override
    protected void onDialogClosing() {
        // Dialog does nothing after its closing - it's the same as clicking the OK button
    }

    protected void initializeDialog(String userMessage) {
        var userMessageArea = getUserMessageArea(userMessage);
        var continueButton = new JButton("OK");
        continueButton.addActionListener(_ -> dispose());
        setButtonHoverAsClick(continueButton);
        JPanel buttonsPanel = getButtonsPanel(continueButton);

        JPanel mainPanel = getDefaultMainPanel();
        mainPanel.add(new JScrollPane(userMessageArea), BorderLayout.CENTER);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);

        setDefaultSizeAndPosition(0.2, 0.2);
        displayPopup();
    }
}
