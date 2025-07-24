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
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

import static java.lang.Boolean.parseBoolean;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

public class AgentConfig {
    private static final Logger LOG = LoggerFactory.getLogger(AgentConfig.class);
    private static final Properties properties = loadProperties();
    public enum ModelProvider {
        GOOGLE,
        OPENAI,
        GROQ
    }

    public enum GoogleApiProvider {
        STUDIO_AI,
        VERTEX_AI
    }

    public enum RagDbProvider {
        CHROMA // Add other providers here if needed
    }

    // -----------------------------------------------------
    // Constants
    private static final String CONFIG_FILE = "config.properties";

    // Main Config
    private static final int START_PORT = loadPropertyAsInteger("port", "PORT", "7070");
    private static final boolean UNATTENDED_MODE = parseBoolean(getProperty("unattended.mode", "UNATTENDED_MODE", "false"));
    private static final String HOST = getRequiredProperty("host", "AGENT_HOST");
    private static final boolean DEBUG_MODE = parseBoolean(getProperty("test.mode", "TEST_MODE", "false"));
    private static final String SCREENSHOTS_SAVE_FOLDER = getProperty("screenshots.save.folder", "SCREENSHOTS_SAVE_FOLDER", "screens");

    // RAG Config
    private static final RagDbProvider VECTOR_DB_PROVIDER = stream(RagDbProvider.values())
            .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(getProperty("vector.db.provider", "VECTOR_DB_PROVIDER", "chroma")))
            .findAny()
            .orElseThrow(() -> new IllegalArgumentException(("%s is not a supported RAG DB provider. Supported ones: %s".formatted(
                    getProperty("vector.db.provider", "VECTOR_DB_PROVIDER", "chroma"), Arrays.toString(RagDbProvider.values())))));
    private static final String VECTOR_DB_URL = getRequiredProperty("vector.db.url", "VECTOR_DB_URL");
    private static final int RETRIEVER_TOP_N = loadPropertyAsInteger("retriever.top.n", "RETRIEVER_TOP_N", "3");

    // Model Config
    private static final ModelProvider MODEL_PROVIDER = stream(ModelProvider.values())
            .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(getProperty("model.provider", "MODEL_PROVIDER", "google")))
            .findAny()
            .orElseThrow(() -> new IllegalArgumentException(("%s is not a supported model provider. Supported ones: %s".formatted(
                    getProperty("model.provider", "MODEL_PROVIDER", "google"), Arrays.toString(ModelProvider.values())))));
    private static final String INSTRUCTION_MODEL_NAME = getProperty("instruction.model.name", "INSTRUCTION_MODEL_NAME", "gemini-2.0-flash");
    private static final String VISION_MODEL_NAME = getProperty("vision.model.name", "VISION_MODEL_NAME", "gemini-2.5-pro-exp-03-25");
    private static final int MAX_OUTPUT_TOKENS = loadPropertyAsInteger("model.max.output.tokens", "MAX_OUTPUT_TOKENS", "5000");
    private static final double TEMPERATURE = loadPropertyAsDouble("model.temperature", "TEMPERATURE", "0.0");
    private static final double TOP_P = loadPropertyAsDouble("model.top.p", "TOP_P", "1.0");
    private static final boolean MODEL_LOGGING_ENABLED = parseBoolean(getProperty("model.logging.enabled", "LOG_MODEL_OUTPUT", "false"));
    private static final boolean THINKING_OUTPUT_ENABLED = parseBoolean(getProperty("thinking.output.enabled", "OUTPUT_THINKING", "false"));
    private static final int GEMINI_THINKING_BUDGET = loadPropertyAsInteger("gemini.thinking.budget", "GEMINI_THINKING_BUDGET", "5000");
    private static final int MAX_RETRIES = loadPropertyAsInteger("model.max.retries", "MAX_RETRIES", "10");

    // Google API Config (Only relevant if model.provider is Google)
    private static final GoogleApiProvider GOOGLE_API_PROVIDER = stream(GoogleApiProvider.values())
            .filter(provider -> provider.name().toLowerCase().equalsIgnoreCase(getProperty("google.api.provider", "GOOGLE_API_PROVIDER", "studio_ai")))
            .findAny()
            .orElseThrow(() -> new IllegalArgumentException(("%s is not a supported Google API provider. Supported ones: %s".formatted(
                    getProperty("google.api.provider", "GOOGLE_API_PROVIDER", "studio_ai"), Arrays.toString(GoogleApiProvider.values())))));
    private static final String GOOGLE_API_TOKEN = getRequiredProperty("google.api.token", "GOOGLE_AI_TOKEN");
    private static final String GOOGLE_PROJECT = getRequiredProperty("google.project", "GOOGLE_PROJECT");
    private static final String GOOGLE_LOCATION = getRequiredProperty("google.location", "GOOGLE_LOCATION");

    // OpenAI API Config
    private static final String OPENAI_API_KEY = getRequiredProperty("azure.openai.api.key", "OPENAI_API_KEY");
    private static final String OPENAI_API_ENDPOINT = getRequiredProperty("azure.openai.endpoint", "OPENAI_API_ENDPOINT");

    // OpenAI API Config
    private static final String GROQ_API_KEY = getRequiredProperty("groq.api.key", "GROQ_API_KEY");
    private static final String GROQ_API_ENDPOINT = getRequiredProperty("groq.endpoint", "GROQ_ENDPOINT");

    // Timeout and Retry Config
    private static final int TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS = loadPropertyAsInteger("test.step.execution.retry.timeout.millis", "TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS", "10000");
    private static final int TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS = loadPropertyAsInteger("test.step.execution.retry.interval.millis", "TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS", "1000");
    private static final int VERIFICATION_RETRY_TIMEOUT_MILLIS = loadPropertyAsInteger("verification.retry.timeout.millis", "VERIFICATION_RETRY_TIMEOUT_MILLIS", "10000");
    private static final int ACTION_VERIFICATION_DELAY_MILLIS = loadPropertyAsInteger("action.verification.delay.millis", "ACTION_VERIFICATION_DELAY_MILLIS", "1000");

    // -----------------------------------------------------
    // Main Config
    public static boolean isUnattendedMode() {
        return UNATTENDED_MODE;
    }

    public static int getStartPort() {
        return START_PORT;
    }
    public static String getHost() {
        return HOST;
    }
    public static boolean isDebugMode() {
        return DEBUG_MODE;
    }

    public static String getScreenshotsSaveFolder() {
        return SCREENSHOTS_SAVE_FOLDER;
    }

    // -----------------------------------------------------
    // RAG Config
    public static RagDbProvider getVectorDbProvider() {
        return VECTOR_DB_PROVIDER;
    }
    public static String getVectorDbUrl() {
        return VECTOR_DB_URL;
    }
    public static int getRetrieverTopN() {
        return RETRIEVER_TOP_N;
    }

    // -----------------------------------------------------
    // Model Config
    public static ModelProvider getModelProvider() {
        return MODEL_PROVIDER;
    }
    public static String getInstructionModelName() {
        return INSTRUCTION_MODEL_NAME;
    }
    public static String getVisionModelName() {
        return VISION_MODEL_NAME;
    }
    public static int getMaxOutputTokens() {
        return MAX_OUTPUT_TOKENS;
    }
    public static double getTemperature() {
        return TEMPERATURE;
    }
    public static double getTopP() {
        return TOP_P;
    }
    public static boolean isModelLoggingEnabled() {
        return MODEL_LOGGING_ENABLED;
    }
    public static boolean isThinkingOutputEnabled() {
        return THINKING_OUTPUT_ENABLED;
    }
    public static int getGeminiThinkingBudget() {
        return GEMINI_THINKING_BUDGET;
    }
    public static int getMaxRetries() {
        return MAX_RETRIES;
    }

    // -----------------------------------------------------
    // Google API Config (Only relevant if model.provider is Google)
    public static GoogleApiProvider getGoogleApiProvider() {
        return GOOGLE_API_PROVIDER;
    }
    public static String getGoogleApiToken() {
        return GOOGLE_API_TOKEN;
    }
    public static String getGoogleProject() {
        return GOOGLE_PROJECT;
    }
    public static String getGoogleLocation() {
        return GOOGLE_LOCATION;
    }

    // -----------------------------------------------------
    // OpenAI API Config
    public static String getOpenAiApiKey() {
        return OPENAI_API_KEY;
    }
    public static String getOpenAiEndpoint() {
        return OPENAI_API_ENDPOINT;
    }

    // -----------------------------------------------------
    // Groq API Config
    public static String getGroqApiKey() {
        return GROQ_API_KEY;
    }
    public static String getGroqEndpoint() {
        return GROQ_API_ENDPOINT;
    }

    // -----------------------------------------------------
    // Timeout and Retry Config
    public static int getTestStepExecutionRetryTimeoutMillis() {
        return TEST_STEP_EXECUTION_RETRY_TIMEOUT_MILLIS;
    }
    public static int getTestStepExecutionRetryIntervalMillis() {
        return TEST_STEP_EXECUTION_RETRY_INTERVAL_MILLIS;
    }
    public static int getVerificationRetryTimeoutMillis() {
        return VERIFICATION_RETRY_TIMEOUT_MILLIS;
    }
    public static int getActionVerificationDelayMillis() {
        return ACTION_VERIFICATION_DELAY_MILLIS;
    }

    // -----------------------------------------------------
    // Element Config
    private static final String ELEMENT_BOUNDING_BOX_COLOR_NAME = getRequiredProperty("element.bounding.box.color", "BOUNDING_BOX_COLOR"); // Element Config
    public static String getElementBoundingBoxColorName() {
        return ELEMENT_BOUNDING_BOX_COLOR_NAME;
    }
    private static final double ELEMENT_RETRIEVAL_MIN_TARGET_SCORE = Double.parseDouble(getProperty("element.retrieval.min.target.score", "ELEMENT_RETRIEVAL_MIN_TARGET_SCORE", "0.85"));
    public static double getElementRetrievalMinTargetScore() {
        return ELEMENT_RETRIEVAL_MIN_TARGET_SCORE;
    }
    private static final double ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE = Double.parseDouble(getProperty("element.retrieval.min.general.score", "ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE", "0.4"));
    public static double getElementRetrievalMinGeneralScore() {
        return ELEMENT_RETRIEVAL_MIN_GENERAL_SCORE;
    }
    private static final double ELEMENT_RETRIEVAL_MIN_PAGE_RELEVANCE_SCORE = Double.parseDouble(getProperty("element.retrieval.min.page.relevance.score", "ELEMENT_RETRIEVAL_MIN_PAGE_RELEVANCE_SCORE", "0.5"));
    public static double getElementRetrievalMinPageRelevanceScore() {
        return ELEMENT_RETRIEVAL_MIN_PAGE_RELEVANCE_SCORE;
    }
    private static final double ELEMENT_LOCATOR_VISUAL_SIMILARITY_THRESHOLD = Double.parseDouble(getProperty("element.locator.visual.similarity.threshold", "VISUAL_SIMILARITY_THRESHOLD", "0.8"));
    public static double getElementLocatorVisualSimilarityThreshold() {
        return ELEMENT_LOCATOR_VISUAL_SIMILARITY_THRESHOLD;
    }
    private static final int ELEMENT_LOCATOR_TOP_VISUAL_MATCHES = loadPropertyAsInteger("element.locator.top.visual.matches",
            "TOP_VISUAL_MATCHES_TO_FIND",
            "3");
    public static int getElementLocatorTopVisualMatches() {
        return ELEMENT_LOCATOR_TOP_VISUAL_MATCHES;
    }

    private static final double FOUND_MATCHES_DIMENSION_DEVIATION_RATIO = Double.parseDouble(getProperty("element.locator.found.matches.dimension.deviation.ratio", "FOUND_MATCHES_DIMENSION_DEVIATION_RATIO", "0.3"));
    public static double getFoundMatchesDimensionDeviationRatio() {
        return FOUND_MATCHES_DIMENSION_DEVIATION_RATIO;
    }

    private static final double ELEMENT_LOCATOR_MIN_INTERSECTION_PERCENTAGE = Double.parseDouble(getProperty("element.locator.min.intersection.area.ratio", "MIN_INTERSECTION_PERCENTAGE", "0.8"));
    public static double getElementLocatorMinIntersectionPercentage() {
        return ELEMENT_LOCATOR_MIN_INTERSECTION_PERCENTAGE;
    }

    // -----------------------------------------------------
    // User UI dialogs
    private static final int DIALOG_DEFAULT_HORIZONTAL_GAP = loadPropertyAsInteger("dialog.default.horizontal.gap", "DIALOG_DEFAULT_HORIZONTAL_GAP", "10");
    public static int getDialogDefaultHorizontalGap() {
        return DIALOG_DEFAULT_HORIZONTAL_GAP;
    }
    private static final int DIALOG_DEFAULT_VERTICAL_GAP = loadPropertyAsInteger("dialog.default.vertical.gap", "DIALOG_DEFAULT_VERTICAL_GAP", "10");
    public static int getDialogDefaultVerticalGap() {
        return DIALOG_DEFAULT_VERTICAL_GAP;
    }
    private static final String DIALOG_DEFAULT_FONT_TYPE = getProperty("dialog.default.font.type", "DIALOG_DEFAULT_FONT_TYPE", "Dialog");
    public static String getDialogDefaultFontType() {
        return DIALOG_DEFAULT_FONT_TYPE;
    }
    private static final int DIALOG_USER_INTERACTION_CHECK_INTERVAL_MILLIS = loadPropertyAsInteger("dialog.user.interaction.check.interval.millis", "DIALOG_USER_INTERACTION_CHECK_INTERVAL_MILLIS",
            "100");
    public static int getDialogUserInteractionCheckIntervalMillis() {
        return DIALOG_USER_INTERACTION_CHECK_INTERVAL_MILLIS;
    }
    private static final int DIALOG_DEFAULT_FONT_SIZE = loadPropertyAsInteger("dialog.default.font.size", "DIALOG_DEFAULT_FONT_SIZE", "13");
    public static int getDialogDefaultFontSize() {
        return DIALOG_DEFAULT_FONT_SIZE;
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
