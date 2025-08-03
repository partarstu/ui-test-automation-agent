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
package org.tarik.ta.rag.model;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;
import static org.tarik.ta.utils.ImageUtils.convertBase64ToImage;
import static org.tarik.ta.utils.ImageUtils.convertImageToBase64;
import static org.tarik.ta.rag.model.UiElement.MetadataField.*;

// TODO: Modify this entity to let multiple screenshots per UI element be stored, user dialogs will have to be extended as well
public record UiElement(UUID uuid,
                        String name,
                        String ownDescription,
                        String anchorsDescription,
                        String pageSummary,
                        Screenshot screenshot) {
    public enum MetadataField {
        ID(Metadata::getUUID, UUID.class),
        NAME(Metadata::getString, String.class),
        OWN_DESCRIPTION(Metadata::getString, String.class),
        ANCHORS_DESCRIPTION(Metadata::getString, String.class),
        PAGE_SUMMARY(Metadata::getString, String.class),
        SCREENSHOT_FILE_EXTENSION(Metadata::getString, String.class),
        SCREENSHOT_MIME_TYPE(Metadata::getString, String.class),
        SCREENSHOT_IMAGE(Metadata::getString, String.class);

        private final ValueProvider<?> valueProvider;

        <T> MetadataField(BiFunction<Metadata, String, T> valueProvider, Class<T> valueClass) {
            this.valueProvider = new ValueProvider<>(valueProvider, valueClass);
        }

        private <T> Optional<T> getValueFromMetadata(Metadata metadata) {
            try {
                return Optional.of(this.getValueFromMetadata(metadata, (ValueProvider<T>) valueProvider));
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        private <T> T getValueFromMetadata(Metadata metadata, ValueProvider<T> valueProvider) {
            return valueProvider.valueClass().cast(valueProvider.valueProvider().apply(metadata, this.name()));
        }
    }

    public TextSegment asTextSegment() {
        return TextSegment.from(getTextRepresentation(), Metadata.from(getMetadata()));
    }

    public static UiElement fromTextSegment(TextSegment textSegment) {
        requireNonNull(textSegment);
        var metadata = textSegment.metadata();

        var id = ID.<UUID>getValueFromMetadata(metadata).orElseThrow();
        var name = NAME.<String>getValueFromMetadata(metadata).orElseThrow();
        var ownDescription = OWN_DESCRIPTION.<String>getValueFromMetadata(metadata).orElseThrow();
        var anchorsDescription = ANCHORS_DESCRIPTION.<String>getValueFromMetadata(metadata).orElse("");
        var pageSummary = PAGE_SUMMARY.<String>getValueFromMetadata(metadata).orElse("");
        var screenshotFileExtension = SCREENSHOT_FILE_EXTENSION.<String>getValueFromMetadata(metadata).orElseThrow();
        var screenshotMimeType = SCREENSHOT_MIME_TYPE.<String>getValueFromMetadata(metadata).orElseThrow();
        var screenshotEncodedString = SCREENSHOT_IMAGE.<String>getValueFromMetadata(metadata).orElseThrow();

        return new UiElement(id, name, ownDescription, anchorsDescription, pageSummary,
                new Screenshot(screenshotFileExtension, screenshotMimeType, screenshotEncodedString));
    }

    public record Screenshot(String fileExtension, String mimeType, String base64EncodedImage) {
        public static Screenshot fromBufferedImage(BufferedImage image, String fileExtension) {
            String mimeType = "image/" + fileExtension;
            String base64EncodedImage = convertImageToBase64(image, fileExtension);
            return new Screenshot(fileExtension, mimeType, base64EncodedImage);
        }

        public BufferedImage toBufferedImage() {
            return convertBase64ToImage(base64EncodedImage);
        }
    }

    @NotNull
    @Override
    public String toString() {
        return new StringJoiner(", ", UiElement.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("ownDescription='" + ownDescription + "'")
                .add("anchorsDescription='" + anchorsDescription + "'")
                .add("pageSummary='" + pageSummary + "'")
                .toString();
    }

    private String getTextRepresentation() {
        return name.trim();
    }

    private Map<String, Object> getMetadata() {
        return Map.of(
                ID.name(), uuid,
                NAME.name(), name,
                OWN_DESCRIPTION.name(), ownDescription,
                ANCHORS_DESCRIPTION.name(), anchorsDescription,
                PAGE_SUMMARY.name(), pageSummary,
                SCREENSHOT_FILE_EXTENSION.name(), screenshot.fileExtension(),
                SCREENSHOT_MIME_TYPE.name(), screenshot.mimeType(),
                SCREENSHOT_IMAGE.name(), screenshot.base64EncodedImage()
        );
    }

    private record ValueProvider<T>(BiFunction<Metadata, String, T> valueProvider, Class<T> valueClass) {
    }
}


