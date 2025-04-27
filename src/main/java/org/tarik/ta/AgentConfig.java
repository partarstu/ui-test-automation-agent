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
package org.tarik.ta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.utils.CommonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

import static com.google.common.io.Files.newReader;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

public class AgentConfig {
    private static final Logger LOG = LoggerFactory.getLogger(AgentConfig.class);
    private static final Properties properties = loadProperties();
    private static final String CONFIG_FILE = "config.properties";

    public enum ModelProvider {
        GOOGLE,
        OPENAI
    }

    public enum GoogleApiProvider {
        STUDIO_AI,
        VERTEX_AI
    }

    public enum RagDbProvider {
        CHROMA // Add other providers here if needed
    }

    // -----------------------------------------------------
    // Main Config
    public static boolean isUnattendedMode() {
        return Boolean.parseBoolean(getProperty("unattended.mode", "UNATTENDED_MODE", "false"));
    }

    public static int getStartPort() {
        return loadPropertyAsInteger("port", "PORT", "7070");
    }

    public static boolean isTestMode() {
        return Boolean.parseBoolean(getProperty("test.mode", "TEST_MODE", "false"));
    }

    // -----------------------------------------------------
    // RAG Config
    public static RagDbProvider getVectorDbProvider() {
        String providerName = getProperty("vector.db.provider", "VECTOR_DB_PROVIDER", "chroma");
        return stream(RagDbProvider.values())
                .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(providerName))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(("%s is not a supported RAG DB provider. Supported ones: %s".formatted(
                        providerName, Arrays.toString(RagDbProvider.values())))));
    }

    public static String getVectorDbUrl() {
        return getRequiredProperty("vector.db.url", "VECTOR_DB_URL");
    }

    public static int getRetrieverTopN() {
        return loadPropertyAsInteger("retriever.top.n", "RETRIEVER_TOP_N", "3");
    }

    // -----------------------------------------------------
    // Model Config
    public static ModelProvider getModelProvider() {
        var providerName = getProperty("model.provider", "MODEL_PROVIDER", "google");
        return stream(ModelProvider.values())
                .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(providerName))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(("%s is not a supported model provider. Supported ones: %s".formatted(
                        providerName, Arrays.toString(ModelProvider.values())))));
    }

    public static String getInstructionModelName() {
        return getProperty("instruction.model.name", "INSTRUCTION_MODEL_NAME", "gemini-2.0-flash");
    }

    public static String getVisionModelName() {
        return getProperty("vision.model.name", "VISION_MODEL_NAME", "gemini-2.5-pro-exp-03-25");
    }

    public static int getMaxOutputTokens() {
        return loadPropertyAsInteger("model.max.output.tokens", "MAX_OUTPUT_TOKENS", "5000");
    }

    public static double getTemperature() {
        return loadPropertyAsDouble("model.temperature", "TEMPERATURE", "0.0");
    }

    public static double getTopP() {
        return loadPropertyAsDouble("model.top.p", "TOP_P", "1.0");
    }

    public static int getMaxRetries() {
        return loadPropertyAsInteger("model.max.retries", "MAX_RETRIES", "10");
    }

    // -----------------------------------------------------
    // Google API Config (Only relevant if model.provider is Google)
    public static GoogleApiProvider getGoogleApiProvider() {
        var providerName = getProperty("google.api.provider", "GOOGLE_API_PROVIDER", "studio_ai");
        return stream(GoogleApiProvider.values())
                .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(providerName))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(("%s is not a supported Google API provider. Supported ones: %s".formatted(
                        providerName, Arrays.toString(GoogleApiProvider.values())))));
    }

    public static String getGoogleApiToken() {
        return getRequiredProperty("google.api.token", "GOOGLE_AI_TOKEN");
    }

    public static String getGoogleProject() {
        return getRequiredProperty("google.project", "GOOGLE_PROJECT");
    }

    public static String getGoogleLocation() {
        return getRequiredProperty("google.location", "GOOGLE_LOCATION");
    }

    // -----------------------------------------------------
    // OpenAI API Config
    public static String getOpenAiApiKey() {
        return getRequiredProperty("openai.api.key", "OPENAI_API_KEY");
    }

    public static String getOpenAiEndpoint() {
        return getRequiredProperty("openai.api.endpoint", "OPENAI_API_ENDPOINT");
    }

    // -----------------------------------------------------
    // Timeout and Retry Config
    public static int getTestStepExecutionRetryTimeoutMillis() {
        return loadPropertyAsInteger("test.step.execution.retry.timeout.millis",
                "TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS", "10000");
    }

    public static int getTestStepExecutionRetryIntervalMillis() {
        return loadPropertyAsInteger("test.step.execution.retry.interval.millis",
                "TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS",
                "1000");
    }

    public static int getVerificationRetryTimeoutMillis() {
        return loadPropertyAsInteger("verification.retry.timeout.millis",
                "VERIFICATION_RETRY_TIMEOUT_MILLIS", "10000");
    }

    public static int getActionVerificationDelayMillis() {
        return loadPropertyAsInteger("action.verification.delay.millis",
                "ACTION_VERIFICATION_DELAY_MILLIS", "1000");
    }


    // -----------------------------------------------------
    // Element Config
    public static String getElementBoundingBoxColorName() {
        return getRequiredProperty("element.bounding.box.color", "BOUNDING_BOX_COLOR");
    }

    public static double getElementRetrievalMinTargetScore() {
        return Double.parseDouble(getProperty("element.retrieval.min.target.score", "ELEMENT_RETRIEVAL_MIN_TARGET_SCORE", "0.85"));
    }

    public static double getElementRetrievalMinGeneralScore() {
        return Double.parseDouble(getProperty("element.retrieval.min.general.score", "ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE", "0.4"));
    }

    public static double getElementLocatorVisualSimilarityThreshold() {
        return Double.parseDouble(getProperty("element.locator.visual.similarity.threshold", "VISUAL_SIMILARITY_THRESHOLD", "0.8"));
    }

    public static int getElementLocatorTopVisualMatches() {
        return loadPropertyAsInteger("element.locator.top.visual.matches",
                "TOP_VISUAL_MATCHES_TO_FIND",
                "3");
    }

    // -----------------------------------------------------
    // User UI dialogs
    public static int getDialogDefaultHorizontalGap() {
        return loadPropertyAsInteger("dialog.default.horizontal.gap", "DIALOG_DEFAULT_HORIZONTAL_GAP", "10");
    }

    public static int getDialogDefaultVerticalGap() {
        return loadPropertyAsInteger("dialog.default.vertical.gap", "DIALOG_DEFAULT_VERTICAL_GAP", "10");
    }

    public static String getDialogDefaultFontType() {
        return getProperty("dialog.default.font.type", "DIALOG_DEFAULT_FONT_TYPE", "Dialog");
    }

    public static int getDialogUserInteractionCheckIntervalMillis() {
        return loadPropertyAsInteger("dialog.user.interaction.check.interval.millis", "DIALOG_USER_INTERACTION_CHECK_INTERVAL_MILLIS",
                "100");
    }

    public static int getDialogDefaultFontSize() {
        return loadPropertyAsInteger("dialog.default.font.size", "DIALOG_DEFAULT_FONT_SIZE", "13");
    }

    // -----------------------------------------------------
    // Private methods
    private static Properties loadProperties() {
        var properties = new Properties();
        try (InputStream inputStream = AgentConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                LOG.error("Cannot find resource file '{}' in classpath.", CONFIG_FILE);
                throw new IOException("Cannot find resource: " + CONFIG_FILE);
            }
            properties.load(new java.io.InputStreamReader(inputStream, UTF_8));
            LOG.info("Loaded properties from " + CONFIG_FILE);
            return properties;
        } catch (IOException e) {
            LOG.error("Error loading properties file " + CONFIG_FILE, e);
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<String> getProperty(String key, String envVar) {
        var envVariableOptional = ofNullable(envVar)
                .map(System::getenv)
                .map(String::trim)
                .filter(CommonUtils::isNotBlank);
        if (envVariableOptional.isPresent()) {
            LOG.debug("Using environment variable '{}' for key '{}'", envVar, key);
            return envVariableOptional;
        } else {
            var propertyFileValueOptional = ofNullable(properties.getProperty(key))
                    .map(String::trim)
                    .filter(CommonUtils::isNotBlank);
            if (propertyFileValueOptional.isPresent()) {
                LOG.debug("Using property file value for key '{}'", key);
                return propertyFileValueOptional;
            } else {
                return empty();
            }
        }
    }

    private static String getProperty(String key, String envVar, String defaultValue) {
        return getProperty(key, envVar).orElseGet(() -> {
            LOG.debug("Using default value for key '{}'", key);
            return defaultValue;
        });
    }

    private static String getRequiredProperty(String key, String envVar) {
        return getProperty(key, envVar).orElseThrow(() -> new IllegalStateException(("The value of required property '%s' must be either " +
                "present in the properties file, or in the environment variable '%s'").formatted(key, envVar)));
    }

    private static int loadPropertyAsInteger(String propertyKey, String envVar, String defaultValue) {
        var value = getProperty(propertyKey, envVar, defaultValue);
        return CommonUtils.parseStringAsInteger(value).orElseThrow(() -> new IllegalArgumentException(
                "The value of property '%s' is not a correct integer value:%s".formatted(propertyKey, value)));
    }

    private static double loadPropertyAsDouble(String propertyKey, String envVar, String defaultValue) {
        var value = getProperty(propertyKey, envVar, defaultValue);
        return CommonUtils.parseStringAsDouble(value).orElseThrow(() -> new IllegalArgumentException(
                "The value of property '%s' is not a correct integer value:%s".formatted(propertyKey, value)));
    }
}