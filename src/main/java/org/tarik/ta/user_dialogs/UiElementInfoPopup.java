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

import org.jetbrains.annotations.NotNull;
import org.tarik.ta.rag.model.UiElement;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class UiElementInfoPopup extends AbstractDialog {
    private static final int FONT_SIZE = 4;
    private final JTextArea nameField;
    private final JTextArea descriptionArea;
    private final JTextArea anchorsArea;
    private final UiElement originalElement;
    private boolean windowClosedByUser = false;

    private UiElementInfoPopup(UiElement originalElement) {
        super("UI Element Info");

        this.originalElement = originalElement;
        JPanel panel = getDefaultMainPanel();
        var userMessageArea = getUserMessageArea("Please revise, and if needed, modify the following info regarding the element");

        panel.add(new JScrollPane(userMessageArea), BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        nameField = addLabelWithValueField("Name", originalElement.name(), contentPanel);
        descriptionArea = addLabelWithValueField("Description", originalElement.ownDescription(), contentPanel);
        anchorsArea = addLabelWithValueField("Surrounding UI elements description", originalElement.anchorsDescription(), contentPanel);

        panel.add(contentPanel, BorderLayout.CENTER);

        JButton doneButton = new JButton("Done");
        doneButton.addActionListener(_ -> dispose());
        JPanel buttonsPanel = getButtonsPanel(doneButton);
        panel.add(buttonsPanel, BorderLayout.SOUTH);

        add(panel);
        setDefaultSizeAndPosition(0.5, 0.6);
        displayPopup();
    }

    @NotNull
    private JTextArea addLabelWithValueField(String label, String value, JPanel panel) {
        JTextArea nameField = new JTextArea(value.trim());
        nameField.setLineWrap(true);
        nameField.setWrapStyleWord(true);

        JLabel nameLabel = new JLabel(("<html><font size='%d'><b>%s:</b></font></html>").formatted(FONT_SIZE, label));
        nameLabel.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(nameLabel, BorderLayout.WEST);

        JScrollPane scrollPane = new JScrollPane(nameField);
        scrollPane.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(scrollPane, BorderLayout.CENTER);
        return nameField;
    }

    @Override
    protected void onDialogClosing() {
        windowClosedByUser = true;
    }

    private UiElement getUiElement() {
        if(!windowClosedByUser){
            return new UiElement(originalElement.uuid(), nameField.getText().trim(), descriptionArea.getText().trim(),
                    anchorsArea.getText().trim(), originalElement.screenshot());
        } else {
         return null;
        }
    }

    public static Optional<UiElement> displayAndGetUpdatedElement(@NotNull UiElement elementDraftFromModel) {
        var popup = new UiElementInfoPopup(elementDraftFromModel);
        waitForUserInteractions(popup);
        return ofNullable(popup.getUiElement());
    }
}
