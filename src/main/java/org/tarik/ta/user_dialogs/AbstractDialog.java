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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import static javax.swing.text.StyleConstants.setAlignment;
import static org.tarik.ta.utils.CommonUtils.sleepMillis;

public abstract class AbstractDialog extends JFrame {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDialog.class);
    private static final int DIALOG_DEFAULT_VERTICAL_GAP = AgentConfig.getDialogDefaultVerticalGap();
    private static final int DIALOG_DEFAULT_HORIZONTAL_GAP = AgentConfig.getDialogDefaultHorizontalGap();

    public AbstractDialog(String title) throws HeadlessException {
        super(title);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                LOG.warn("User closed the '{}' dialog.", title);
                onDialogClosing();
            }
        });
    }

    protected abstract void onDialogClosing();

    @NotNull
    protected static JPanel getDefaultMainPanel() {
        // Use AgentConfig to get values
        JPanel mainPanel = new JPanel(new BorderLayout(DIALOG_DEFAULT_HORIZONTAL_GAP, DIALOG_DEFAULT_VERTICAL_GAP));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(DIALOG_DEFAULT_VERTICAL_GAP, DIALOG_DEFAULT_HORIZONTAL_GAP,
                DIALOG_DEFAULT_VERTICAL_GAP, DIALOG_DEFAULT_HORIZONTAL_GAP));
        return mainPanel;
    }

    @NotNull
    protected static JTextPane getUserMessageArea(String message, int fontSize) {
        JTextPane messageArea = new JTextPane(); // Use JTextPane for StyledDocument
        messageArea.setEditable(false);
        messageArea.setOpaque(false);
        // Use AgentConfig to get font type
        messageArea.setFont(new Font(AgentConfig.getDialogDefaultFontType(), Font.PLAIN, fontSize));
        var styledDocument = messageArea.getStyledDocument();
        SimpleAttributeSet center = new SimpleAttributeSet();
        setAlignment(center, StyleConstants.ALIGN_CENTER);
        try {
            styledDocument.insertString(0, message, null);
            styledDocument.setParagraphAttributes(0, styledDocument.getLength(), center, false);
        } catch (BadLocationException e) {
            LOG.error("Couldn't display the popup user message", e);
        }

        return messageArea;
    }

    @NotNull
    protected static JTextPane getUserMessageArea(String message) {
        // Use AgentConfig to get default font size
        return getUserMessageArea(message, AgentConfig.getDialogDefaultFontSize());
    }

    protected static void setButtonHoverAsClick(AbstractButton button, Runnable actionAfterClick) {
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.doClick();
                actionAfterClick.run();
            }
        });
    }

    protected static void setButtonHoverAsClick(AbstractButton button) {
        setButtonHoverAsClick(button, () -> {
        });
    }

    protected static void waitForUserInteractions(AbstractDialog popup) {
        while (popup.isVisible()) {
            // Use AgentConfig to get interval
            sleepMillis(AgentConfig.getDialogUserInteractionCheckIntervalMillis());
        }
    }

    @NotNull
    protected static JPanel getButtonsPanel(JButton... buttons) {
        // Use AgentConfig to get gaps
        var panel = new JPanel(new FlowLayout(FlowLayout.CENTER, DIALOG_DEFAULT_HORIZONTAL_GAP, DIALOG_DEFAULT_VERTICAL_GAP));
        for (JButton button : buttons) {
            panel.add(button);
        }
        return panel;
    }

    protected void displayPopup() {
        setVisible(true);
        toFront();
    }

    protected void setDefaultPosition() {
        setLocationRelativeTo(null);
    }

    private void setDefaultSize(double widthRatio, double heightRatio) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        var desiredWidth = screenSize.getWidth() * widthRatio;
        var desiredHeight = screenSize.getHeight() * heightRatio;
        setSize(new Dimension((int) desiredWidth, (int) desiredHeight));
    }

    protected void setDefaultSizeAndPosition(double widthRatio, double heightRatio) {
        pack();
        setDefaultSize(widthRatio, heightRatio);
        setDefaultPosition();
    }
}