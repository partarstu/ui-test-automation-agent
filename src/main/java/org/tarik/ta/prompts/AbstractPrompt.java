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

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.input.PromptTemplate;
import org.jetbrains.annotations.NotNull;
import org.tarik.ta.utils.ImageUtils;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.langchain4j.data.message.ImageContent.DetailLevel.HIGH;
import static java.lang.System.lineSeparator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

public abstract class AbstractPrompt {
    private static final String SYSTEM_PROMPTS_ROOT_FOLDER = "prompt_templates/system";
    private static final String USER_PROMPTS_ROOT_FOLDER = "prompt_templates/user";
    private static final String DEFAULT_IMAGE_FORMAT = "png";
    private final Map<String, Object> systemMessagePlaceholders;
    private final Map<String, Object> userMessagePlaceholders;

    protected AbstractPrompt(@NotNull Map<String, String> systemMessagePlaceholders,
                             @NotNull Map<String, String> userMessagePlaceholders) {
        this.systemMessagePlaceholders = Map.copyOf(systemMessagePlaceholders);
        this.userMessagePlaceholders = Map.copyOf(userMessagePlaceholders);
    }

    protected static ImageContent singleImageContent(BufferedImage image) {
        return ImageContent.from(ImageUtils.getImage(image, DEFAULT_IMAGE_FORMAT), HIGH);
    }

    protected static TextContent textContent(String text) {
        return TextContent.from(text);
    }

    public UserMessage getUserMessage() {
        var mainContentText = PromptTemplate.from(getUserMessageTemplate()).apply(userMessagePlaceholders).text();
        var additionalContents = getUserMessageAdditionalContents();
        return UserMessage.from(Stream.concat(Stream.of(TextContent.from(mainContentText)), additionalContents.stream()).toList());
    }

    public SystemMessage getSystemMessage() {
        return SystemMessage.from(PromptTemplate.from(getSystemMessageTemplate()).apply(systemMessagePlaceholders).text());
    }

    protected abstract List<Content> getUserMessageAdditionalContents();

    protected abstract String getUserMessageTemplate();

    protected abstract String getSystemMessageTemplate();

    protected static String getPromptFileContent(String pathString) {
        URL resource = AbstractPrompt.class.getClassLoader().getResource(pathString);
        if (resource == null) {
            throw new UncheckedIOException(new IOException("Couldn't find the prompt file: %s".formatted(pathString)));
        }
        try (InputStreamReader reader = new InputStreamReader(resource.openStream(), UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            return bufferedReader.lines().collect(joining(lineSeparator()));
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read the contents of the prompt from the file %s".formatted(pathString), e);
        }
    }

    protected static String getSystemPromptFileContent(String name) {
        return getPromptFileContent("%s/%s".formatted(SYSTEM_PROMPTS_ROOT_FOLDER, name));
    }
}
