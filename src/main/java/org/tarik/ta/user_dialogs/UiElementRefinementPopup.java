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
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static java.awt.Image.SCALE_AREA_AVERAGING;
import static java.util.stream.IntStream.range;

public class UiElementRefinementPopup extends AbstractDialog {
    private static final String DIALOG_TITLE = "UI Elements Refinement";
    private static final String ELEMENT_LABEL_FORMAT = "<html><body style='width: %dpx; font-size: %dpx;'>"
            + "<p><b>%s</b></p><p></p><p><i>%s</i></p></body></html>";
    private static final String ELEMENT_ACTION_DIALOG_TITLE = "Element Action";
    private static final String UPDATE_BUTTON_TEXT = "Update Element";
    private static final String DELETE_BUTTON_TEXT = "Delete Element";
    private static final String ELEMENT_ACTION_DIALOG_MESSAGE = "What do you want to do with this element ?";
    private static final int ELEMENT_ACTION_DIALOG_WIDTH = 300;
    private static final int ELEMENT_ACTION_DIALOG_HEIGHT = 150;
    private static final int ELEMENT_DESCRIPTION_FONT_SIZE = 8;
    private static final int IMAGE_TARGET_WIDTH = 100;
    private static final int ELEMENT_DESCRIPTION_LENGTH = 550;

    private final Map<UiElement, Integer> availableElements = new LinkedHashMap<>();
    private final BiFunction<UiElement, String, UiElement> elementUpdater;
    private final BiConsumer<UiElement, String> elementRemover;
    private final JPanel elementPanel;
    private final String elementDescription;

    private final ExecutorService elementActionExecutor = Executors.newCachedThreadPool();

    private UiElementRefinementPopup(String message, List<UiElement> itemsToRefine, String targetElementDescription,
                                     BiFunction<UiElement, String, UiElement> elementUpdater,
                                     BiConsumer<UiElement, String> elementRemover) {
        super(DIALOG_TITLE);
        this.elementUpdater = elementUpdater;
        this.elementRemover = elementRemover;
        this.elementDescription = targetElementDescription;
        range(0, itemsToRefine.size()).forEach(index -> availableElements.put(itemsToRefine.get(index), index));

        JPanel mainPanel = getDefaultMainPanel();

        var messageArea = getUserMessageArea(message);
        mainPanel.add(messageArea, BorderLayout.NORTH);

        this.elementPanel = new JPanel();
        JScrollPane scrollPane = new JScrollPane(elementPanel);
        refreshElementPanel();
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JButton doneButton = new JButton("Done");
        doneButton.addActionListener(_ -> dispose());
        JPanel buttonsPanel = getButtonsPanel(doneButton);
        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setDefaultSizeAndPosition(0.5, 0.6);
        displayPopup();
    }

    private void showElementActionDialog(UiElement element, String elementDescription) {
        JDialog dialog = new JDialog(this, ELEMENT_ACTION_DIALOG_TITLE, true);
        dialog.setLayout(new FlowLayout());
        JButton updateButton = new JButton(UPDATE_BUTTON_TEXT);
        JButton deleteButton = new JButton(DELETE_BUTTON_TEXT);

        updateButton.addActionListener(_ -> {
            dialog.dispose();
            elementActionExecutor.submit(() -> {
                var updatedElement = elementUpdater.apply(element, elementDescription);
                var position = availableElements.remove(element);
                availableElements.put(updatedElement, position);
                refreshElementPanel();
            });
        });

        deleteButton.addActionListener(_ -> {
            dialog.dispose();
            elementActionExecutor.submit(() -> {
                elementRemover.accept(element, elementDescription);
                availableElements.remove(element);
                refreshElementPanel();
            });
        });

        dialog.add(new JLabel(ELEMENT_ACTION_DIALOG_MESSAGE));
        dialog.add(updateButton);
        dialog.add(deleteButton);
        dialog.setSize(ELEMENT_ACTION_DIALOG_WIDTH, ELEMENT_ACTION_DIALOG_HEIGHT);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void refreshElementPanel() {
        elementPanel.removeAll();
        elementPanel.setLayout(new BoxLayout(elementPanel, BoxLayout.Y_AXIS));
        availableElements.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .forEach(element -> {
                    var elementLabel = getElementLabel(element);
                    elementLabel.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            showElementActionDialog(element, elementDescription);
                        }
                    });
                    elementLabel.setBorder(new MatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
                    elementPanel.add(elementLabel);
                });

        elementPanel.revalidate();
        elementPanel.repaint();
        UiElementRefinementPopup.this.repaint();
    }

    @NotNull
    private JLabel getElementLabel(UiElement element) {
        String labelText = String.format(ELEMENT_LABEL_FORMAT, ELEMENT_DESCRIPTION_LENGTH, ELEMENT_DESCRIPTION_FONT_SIZE, element.name(),
                 element.ownDescription());
        JLabel label = new JLabel(labelText);
        label.setIcon(getImageIcon(element));
        label.setHorizontalTextPosition(SwingConstants.RIGHT);
        label.setVerticalTextPosition(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setIconTextGap(10);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return label;
    }

    @NotNull
    private static ImageIcon getImageIcon(UiElement element) {
        var elementScreenshot = element.screenshot().toBufferedImage();
        var originalWidth = elementScreenshot.getWidth();
        var scalingRatio = ((double) IMAGE_TARGET_WIDTH) / originalWidth;
        var imageTargetHeight = (int) (elementScreenshot.getHeight() * scalingRatio);
        return new ImageIcon(elementScreenshot.getScaledInstance(IMAGE_TARGET_WIDTH, imageTargetHeight, SCALE_AREA_AVERAGING));
    }

    public static void display(@NotNull String message,
                               @NotNull List<UiElement> elementsToRefine,
                               @NotNull String targetElementDescription,
                               @NotNull BiFunction<UiElement, String, UiElement> elementUpdater,
                               @NotNull BiConsumer<UiElement, String> elementRemover) {
        var popup = new UiElementRefinementPopup(message, elementsToRefine, targetElementDescription, elementUpdater, elementRemover);
        waitForUserInteractions(popup);
    }

    @Override
    protected void onDialogClosing() {
        // The user has decided that nothing else needs to be done - it's the same as clicking "Done"
    }
}
