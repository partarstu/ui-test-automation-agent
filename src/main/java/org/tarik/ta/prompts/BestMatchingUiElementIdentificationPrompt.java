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
package org.tarik.ta.prompts;

import com.google.gson.Gson;
import dev.langchain4j.data.message.Content;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.dto.UiElementIdentificationResult;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class BestMatchingUiElementIdentificationPrompt extends StructuredResponsePrompt<UiElementIdentificationResult> {
    private static final String SYSTEM_PROMPT_FILE_NAME = "find_best_matching_ui_element_id.txt";
    private static final String TARGET_ELEMENT_DESCRIPTION_PLACEHOLDER = "target_element_description";
    private static final String ELEMENTS_AGENDA_PLACEHOLDER = "elements_agenda";
    private static final Gson GSON = new Gson();
    private final BufferedImage screenshot;

    private BestMatchingUiElementIdentificationPrompt(@NotNull Map<String, String> systemMessagePlaceholders,
                                                      @NotNull Map<String, String> userMessagePlaceholders,
                                                      @NotNull BufferedImage screenshot) {
        super(systemMessagePlaceholders, userMessagePlaceholders);
        this.screenshot = screenshot;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected List<Content> getUserMessageAdditionalContents() {
        return List.of(
                textContent("The provided to you screenshot:\n"),
                singleImageContent(screenshot)
        );
    }

    @Override
    protected String getUserMessageTemplate() {
        return "The provided to you candidate UI elements:\n{{%s}}".formatted(ELEMENTS_AGENDA_PLACEHOLDER);
    }

    @Override
    protected String getSystemMessageTemplate() {
        return getSystemPromptFileContent(SYSTEM_PROMPT_FILE_NAME);
    }

    @NotNull
    @Override
    public Class<UiElementIdentificationResult> getResponseObjectClass() {
        return UiElementIdentificationResult.class;
    }

    public static class Builder {
        private String targetElementDescription;
        private Collection<UiElementCandidate> uiElementCandidates;
        private BufferedImage screenshot;

        public Builder withTargetElementDescription(@NotNull String description) {
            this.targetElementDescription = description;
            return this;
        }

        public Builder withUiElementCandidates(@NotNull Collection<UiElementCandidate> uiElementCandidates) {
            this.uiElementCandidates = uiElementCandidates;
            return this;
        }

        public Builder withScreenshot(@NotNull BufferedImage screenshot) {
            this.screenshot = requireNonNull(screenshot, "Screenshot cannot be null");
            return this;
        }

        public BestMatchingUiElementIdentificationPrompt build() {
            checkArgument(isNotBlank(targetElementDescription), "Target element description must be set");
            Map<String, String> systemMessagePlaceholders = Map.of(
                    TARGET_ELEMENT_DESCRIPTION_PLACEHOLDER, targetElementDescription
            );

            var uiElementsString = uiElementCandidates.stream()
                    .map(Builder::getElementPropsMap)
                    .map(GSON::toJson)
                    .collect(joining("\n"));
            Map<String, String> userMessagePlaceholders = Map.of(
                    ELEMENTS_AGENDA_PLACEHOLDER, uiElementsString
            );
            return new BestMatchingUiElementIdentificationPrompt(systemMessagePlaceholders, userMessagePlaceholders, screenshot);
        }

        @NotNull
        private static LinkedHashMap<String, String> getElementPropsMap(UiElementCandidate el) {
            var map = new LinkedHashMap<String, String>();
            map.put("ID", el.id());
            //map.put("ID location", "%s of the bounding box".formatted(el.idLocation()));
            map.put("Bounding box color", el.boundingBoxColor());
            map.put("Details", el.details());
            map.put("Description of surrounding elements", el.descriptionOfSurroundingElements());
            return map;
        }
    }

    public record UiElementCandidate(String id, String boundingBoxColor, String details, String descriptionOfSurroundingElements) {
    }
}