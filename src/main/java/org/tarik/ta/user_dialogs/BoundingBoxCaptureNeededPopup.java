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

public class BoundingBoxCaptureNeededPopup extends AbstractConfirmationDialog {
    private BoundingBoxCaptureNeededPopup() {
        super("Further action required");

        initializeDialog("The screenshot of the first screen is to be made and after that you'll " +
                "be asked to highlight the target element on that screenshot. Please make sure that the target element is visible on the " +
                "first screen");
    }

    public static void display() {
        var popup = new BoundingBoxCaptureNeededPopup();
        waitForUserInteractions(popup);
    }
}