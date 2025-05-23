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
package org.tarik.ta.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.tarik.ta.annotations.JsonFieldDescription;
import org.tarik.ta.annotations.JsonClassDescription;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static org.tarik.ta.utils.CommonUtils.isNotBlank;

public class ModelUtils {
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    public static <T> T parseModelResponseAsObject(ChatResponse response, Class<T> objectClass) {
        var objectClassName = objectClass.getSimpleName();
        var responseText = response.aiMessage().text();
        String modelName = response.metadata().modelName();
        checkArgument(isNotBlank(responseText), "Got empty response from %s model expecting %s object.", modelName, objectClassName);
        try {
            return OBJECT_MAPPER.readValue(rectifyJsonResponse(responseText), objectClass);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Couldn't parse the following %s model response as a %s object: %s".formatted(
                    modelName, objectClassName, responseText));
        }
    }

    public static String rectifyJsonResponse(String originalModelResponse) {
        return originalModelResponse.replaceAll("[\\S\\s]*```json", "").replaceAll("```[\\S\\s]*", "");
    }

    public static <T> String getJsonSchemaDescription(Class<T> clazz) {
        Map<String, Object> properties = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            var annotation = field.getAnnotation(JsonFieldDescription.class);
            if (annotation != null) {
                String fieldType = field.getType().getSimpleName();
                properties.put(field.getName(), "%s; %s".formatted(fieldType.toLowerCase(), annotation.value()));
            }
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String extendPromptWithResponseObjectInfo(String prompt, Class<?> objectClass) {
        var responseFormatDescription = ("Output only a valid JSON object representing %s, use the following JSON output structure:\n%s")
                .formatted(getClassDescriptionForPrompt(objectClass), getJsonSchemaDescription(objectClass));
        return "%s\n\n%s".formatted(prompt, responseFormatDescription);
    }

    private static String getClassDescriptionForPrompt(Class<?> objectClass) {
        return Optional.ofNullable(objectClass.getAnnotation(JsonClassDescription.class))
                .map(JsonClassDescription::value)
                .orElseThrow(() -> new IllegalStateException(("The class %s has no @JsonClassDescription annotation needed for its " +
                        "purpose description in the prompt").formatted(objectClass.getSimpleName())));
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

}
