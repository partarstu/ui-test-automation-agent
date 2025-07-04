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
package org.tarik.ta.tools;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.jetbrains.annotations.NotNull;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.exceptions.UserChoseTerminationException;
import org.tarik.ta.exceptions.UserInterruptedExecutionException;
import org.tarik.ta.prompts.BestMatchingUiElementIdentificationPrompt.UiElementCandidate;
import org.tarik.ta.prompts.ElementDescriptionPrompt;
import org.tarik.ta.rag.RetrieverFactory;
import org.tarik.ta.prompts.BestMatchingUiElementIdentificationPrompt;
import org.tarik.ta.rag.model.UiElement;
import org.tarik.ta.rag.model.UiElement.Screenshot;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.rag.UiElementRetriever.RetrievedItem;
import org.tarik.ta.user_dialogs.*;

import java.awt.*;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Optional.*;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.*;
import static javax.swing.JOptionPane.showMessageDialog;
import static org.opencv.imgproc.Imgproc.matchTemplate;
import static org.tarik.ta.model.ModelFactory.getVisionModel;
import static org.tarik.ta.utils.BoundingBoxUtil.drawBoundingBoxes;
import static org.tarik.ta.utils.BoundingBoxUtil.mergeOverlappingRectangles;
import static org.tarik.ta.utils.CommonUtils.*;
import static org.tarik.ta.utils.ImageUtils.*;
import static org.tarik.ta.rag.model.UiElement.Screenshot.fromBufferedImage;
import static org.opencv.imgcodecs.Imgcodecs.imdecode;

import java.util.ArrayList;
import java.util.function.Supplier;

public class ElementLocator extends AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(ElementLocator.class);
    private static final double VISUAL_SIMILARITY_THRESHOLD = AgentConfig.getElementLocatorVisualSimilarityThreshold();
    private static final double MIN_TARGET_RETRIEVAL_SCORE = AgentConfig.getElementRetrievalMinTargetScore();
    private static final double MIN_GENERAL_RETRIEVAL_SCORE = AgentConfig.getElementRetrievalMinGeneralScore();
    private static final int USER_DIALOG_DISMISS_DELAY_MILLIS = 1000;
    private static final String BOUNDING_BOX_COLOR_NAME = AgentConfig.getElementBoundingBoxColorName();
    private static final Color BOUNDING_BOX_COLOR = getColorByName(BOUNDING_BOX_COLOR_NAME);
    private static final int TOP_N_ELEMENTS_TO_RETRIEVE = AgentConfig.getRetrieverTopN();
    private static final int TOP_VISUAL_MATCHES_TO_FIND = AgentConfig.getElementLocatorTopVisualMatches();
    private static final boolean UNATTENDED_MODE = AgentConfig.isUnattendedMode();
    private static final UiElementRetriever elementRetriever = RetrieverFactory.getUiElementRetriever();
    private static final boolean IS_IN_TEST_MODE = AgentConfig.isTestMode();
    private static boolean initialized = false;
    private static final List<Color> availableBoundingBoxColors = List.of(
            Color.RED,
            Color.BLUE,
            Color.GREEN,
            Color.YELLOW,
            Color.BLACK,
            Color.ORANGE,
            Color.PINK,
            Color.CYAN,
            Color.MAGENTA
    );

    private static boolean initializeOpenCv() {
        Loader.load(opencv_java.class);
        return true;
    }

    public static Optional<Rectangle> locateElementOnTheScreen(String elementDescription) {
        if (!initialized) {
            initialized = initializeOpenCv();
        }
        var retrievedElements = elementRetriever.retrieveElementsByScore(elementDescription, TOP_N_ELEMENTS_TO_RETRIEVE,
                MIN_GENERAL_RETRIEVAL_SCORE);
        var matchingUiElements = retrievedElements.stream()
                .filter(retrievedItem -> retrievedItem.score() >= MIN_TARGET_RETRIEVAL_SCORE)
                .map(RetrievedItem::element)
                .toList();
        if (matchingUiElements.isEmpty() && !retrievedElements.isEmpty()) {
            if (UNATTENDED_MODE) {
                var retrievedElementsString = retrievedElements.stream()
                        .map(el -> "%s --> %.1f".formatted(el.element().name(), el.score()))
                        .collect(joining(", "));
                LOG.warn("No UI elements found in vector DB which semantically match the description '{}' with the " +
                                "similarity score > {}. The most similar element names by similarity score are: {}", elementDescription,
                        "%.1f".formatted(MIN_TARGET_RETRIEVAL_SCORE), retrievedElementsString);
                return empty();
            } else {
                // This one happens as soon as DB has some elements, but none of them has the similarity higher than the configured threshold
                promptUserToRefinePossibleCandidateUiElements(elementDescription, retrievedElements);
                return promptUserForNextActionAfterNoElementFound(elementDescription);
            }
        } else if (matchingUiElements.isEmpty()) {
            if (UNATTENDED_MODE) {
                LOG.warn("No UI elements found in vector DB which semantically match the description '{}' with the " +
                        "similarity score > {}.", elementDescription, "%.1f".formatted(MIN_GENERAL_RETRIEVAL_SCORE));
                return empty();
            } else {
                // This one will be seldom, because after at least some elements are in DB, they will be displayed
                NewElementInfoNeededPopup.display(elementDescription);
                return of(promptUserForCreatingNewElement(elementDescription));
            }
        } else {
            LOG.info("Found {} UI element(s) in DB corresponding to the description of '{}'.",
                    matchingUiElements.size(), elementDescription);
            return findElementAndProcessLocationResult(() -> getFinalElementLocation(elementDescription, matchingUiElements),
                    elementDescription);
        }
    }

    private static void promptUserToRefinePossibleCandidateUiElements(String elementDescription, List<RetrievedItem> retrievedElements) {
        List<UiElement> elementsToRefine = retrievedElements.stream()
                .map(RetrievedItem::element)
                .toList();
        var message = ("I haven't found any UI elements in my Database which perfectly match the description '%s'. You could update " +
                "or delete the closest ones matching the mentioned above description in order to have more adequate search results " +
                "next time:").formatted(elementDescription);
        promptUserToRefineUiElements(elementDescription, message, elementsToRefine);
    }

    private static void promptUserToRefineUiElements(String elementDescription, String message, List<UiElement> elementsToRefine) {
        UiElementRefinementPopup.display(message, elementsToRefine, elementDescription,
                (element, _) -> {
                    var clarifiedByUserElement = UiElementInfoPopup.displayAndGetUpdatedElement(element)
                            .orElseThrow(UserInterruptedExecutionException::new);
                    if (!element.equals(clarifiedByUserElement)) {
                        try {
                            elementRetriever.updateElement(element, clarifiedByUserElement);
                        } catch (Exception e) {
                            var logMessage = "Couldn't update the following UI element: " + element;
                            LOG.error(logMessage, e);
                            showMessageDialog(null, "Couldn't update the UI element, see the logs for details");
                        }
                    }

                    return clarifiedByUserElement;
                },
                (element, _) -> {
                    try {
                        elementRetriever.removeElement(element);
                    } catch (Exception e) {
                        var logMessage = "Couldn't delete the following UI element: " + element;
                        LOG.error(logMessage, e);
                        showMessageDialog(null, "Couldn't delete the UI element, see the logs for details");
                    }
                });
    }

    private static Optional<Rectangle> findElementAndProcessLocationResult(Supplier<UiElementLocationResult> resultSupplier,
                                                                           String elementDescription) {
        var locationResult = resultSupplier.get();
        return switch (locationResult) {
            case UiElementLocationResult(boolean patternMatch, var _, var _, Collection<UiElement> elementsUsed) when !patternMatch ->
                    processNoPatternMatchesCase(elementDescription, elementsUsed);
            case UiElementLocationResult(var _, boolean visualMatchByModel, var _, Collection<UiElement> elementsUsed) when
                    !visualMatchByModel -> processNoVisualMatchCase(elementDescription, elementsUsed);
            case UiElementLocationResult(var _, var _, Rectangle boundingBox, var _) when boundingBox != null -> {
                LOG.info("Model has identified the best visual match for the description '{}' located at: {}",
                        elementDescription, boundingBox);
                yield of(getScaledBoundingBox(boundingBox));
            }
            default -> throw new IllegalStateException("Got element location result in unexpected state: " + locationResult);
        };
    }

    private static Optional<Rectangle> processNoVisualMatchCase(String elementDescription, Collection<UiElement> elementsUsed) {
        var rootCause = ("Visual pattern matching provided results, but the model has decided that none of them visually " +
                "matches the description '%s'. Either this is a bug, or the UI has been modified and the saved in DB UI element " +
                "info is obsolete. Do you wish to refine the UI element info or to terminate the execution ?")
                .formatted(elementDescription);
        return processNoElementFoundCaseInAttendedMode(elementDescription, elementsUsed, rootCause);
    }

    private static Optional<Rectangle> processNoPatternMatchesCase(String elementDescription, Collection<UiElement> elementsUsed) {
        var rootCause = ("Visual pattern matching provided no results within deadline. Either this is a bug, or most probably " +
                "the UI has been modified and the saved in DB UI element info is obsolete. The element description is: '%s'. Do " +
                "you wish to refine the UI element info or to terminate the execution ?").formatted(elementDescription);
        return processNoElementFoundCaseInAttendedMode(elementDescription, elementsUsed, rootCause);
    }

    private static Optional<Rectangle> processNoElementFoundCaseInAttendedMode(String elementDescription,
                                                                               Collection<UiElement> elementsUsed,
                                                                               String rootCause) {
        return switch (NoElementFoundPopup.displayAndGetUserDecision(rootCause)) {
            case CONTINUE -> {
                if (!elementsUsed.isEmpty()) {
                    var message = "You could update or delete the elements which were used in the search in order to have " +
                            "more adequate search results next time:";
                    promptUserToRefineUiElements(elementDescription, message, elementsUsed.stream().toList());
                }
                yield promptUserForNextActionAfterNoElementFound(elementDescription);
            }
            case TERMINATE -> {
                logUserTerminationRequest();
                throw new UserChoseTerminationException();
            }
        };
    }

    private static Optional<Rectangle> promptUserForNextActionAfterNoElementFound(String elementDescription) {
        return switch (NextActionAfterNoElementFoundPopup.displayAndGetUserDecision()) {
            case RETRY_SEARCH -> locateElementOnTheScreen(elementDescription);
            case CREATE_NEW_ELEMENT -> {
                sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);
                yield of(promptUserForCreatingNewElement(elementDescription));
            }
            case TERMINATE -> {
                logUserTerminationRequest();
                throw new UserChoseTerminationException();
            }
        };
    }

    private static void logUserTerminationRequest() {
        LOG.warn("The user decided to terminate the execution. Exiting...");
    }

    @NotNull
    private static UiElementLocationResult getFinalElementLocation(String elementDescription, List<UiElement> matchingUiElements) {
        BufferedImage wholeScreenshot = captureScreen();
        var matchedBoundingBoxesByElement = matchingUiElements.stream()
                .collect(toConcurrentMap(uiElement -> uiElement, uiElement -> {
                    var elementScreenshot = uiElement.screenshot().toBufferedImage();
                    var boundingBoxes = findMatchingRegions(wholeScreenshot, elementScreenshot);
                    return mergeOverlappingRectangles(boundingBoxes);
                }));
        int visualMatchesAmount = matchedBoundingBoxesByElement.values().stream().mapToInt(Collection::size).sum();
        var elementNames = matchedBoundingBoxesByElement.keySet().stream().map(UiElement::name).collect(joining("', '", "'", "'"));
        if (visualMatchesAmount < 1) {
            LOG.warn("No matching regions found for the following UI elements: {}", elementNames);
            return new UiElementLocationResult(false, false, null, matchingUiElements);
        } else {
            LOG.info("Found {} visual match(es) corresponding to '{}'. Name(s): {}. Choosing the best one.",
                    visualMatchesAmount, elementDescription, elementNames);
            return getFinalUiElementLocationUsingModel(matchedBoundingBoxesByElement, wholeScreenshot, elementDescription);
        }
    }

    @NotNull
    private static Rectangle promptUserForCreatingNewElement(String originalElementDescription) {
        BoundingBoxCaptureNeededPopup.display();
        sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);
        var elementCaptureResult = UiElementScreenshotCaptureWindow.displayAndGetResult(BOUNDING_BOX_COLOR)
                .orElseThrow(UserInterruptedExecutionException::new);
        if (!elementCaptureResult.success()) {
            throw new IllegalStateException("Couldn't capture UI element bounding box. Please see logs for details");
        }
        var prompt = ElementDescriptionPrompt.builder()
                .withOriginalElementDescription(originalElementDescription)
                .withScreenshot(elementCaptureResult.wholeScreenshotWithBoundingBox())
                .withBoundingBoxColor(BOUNDING_BOX_COLOR)
                .build();

        try (var model = getVisionModel(false)) {
            var uiElementDescriptionResult = model.generateAndGetResponseAsObject(prompt,
                    "generating the description of selected UI element");
            var describedUiElement = new UiElement(randomUUID(), uiElementDescriptionResult.name(),
                    uiElementDescriptionResult.ownDescription(), uiElementDescriptionResult.anchorsDescription(), null);
            var clarifiedByUserElement = UiElementInfoPopup.displayAndGetUpdatedElement(describedUiElement)
                    .orElseThrow(UserInterruptedExecutionException::new);
            var elementBoundingBox = elementCaptureResult.boundingBox();
            initializeAndSaveNewUiElementIntoDb(elementCaptureResult.elementScreenshot(), clarifiedByUserElement);
            TargetElementToGetFocusPopup.display();
            sleepMillis(USER_DIALOG_DISMISS_DELAY_MILLIS);
            return getScaledBoundingBox(elementBoundingBox);
        }
    }

    private static void initializeAndSaveNewUiElementIntoDb(BufferedImage elementScreenshot, UiElement uiElement) {
        Screenshot screenshot = fromBufferedImage(elementScreenshot, "png");
        UiElement uiElementToStore = new UiElement(randomUUID(), uiElement.name(), uiElement.ownDescription(),
                uiElement.anchorsDescription(), screenshot);
        elementRetriever.storeElement(uiElementToStore);
    }

    private static UiElementLocationResult getFinalUiElementLocationUsingModel(
            Map<UiElement, List<Rectangle>> matchedBoundingBoxesByElement,
            BufferedImage screenshot, String targetElementDescription) {
        // First all elements must be plotted onto the screenshot with unique label so that the model knows which ID corresponds to
        // which UI element. We use available colors as unique labels
        var originalElements = matchedBoundingBoxesByElement.keySet();
        var boxedAmount = matchedBoundingBoxesByElement.values().stream().mapToInt(List::size).sum();
        checkArgument(boxedAmount <= availableBoundingBoxColors.size(), "Amount of bounding boxes to plot exceeds the amount " +
                "of available colors to use as labels for disambiguation. Please increase the amount of available colors in the list");
        var colorsToUse = new LinkedList<>(availableBoundingBoxColors);
        AtomicInteger colorCounter = new AtomicInteger();
        var elementsToPlot = matchedBoundingBoxesByElement.entrySet().stream()
                .flatMap(boxesByElement -> boxesByElement.getValue().stream()
                        .map(box -> new PlottedUiElement(String.valueOf(colorCounter.incrementAndGet()),
                                colorsToUse.removeFirst(), boxesByElement.getKey(), box)))
                .toList();
        var resultingScreenshot = cloneImage(screenshot);
        Map<Color, Rectangle> elementBoundingBoxesByLabel = elementsToPlot.stream()
                .collect(toMap(PlottedUiElement::elementColor, PlottedUiElement::boundingBox));

        // Now asking the model to identify the element which is the best fit to the target description
        var uiElementCandidates = getUiElementCandidates(resultingScreenshot, elementBoundingBoxesByLabel, elementsToPlot);
        var prompt = BestMatchingUiElementIdentificationPrompt.builder()
                .withUiElementCandidates(uiElementCandidates)
                .withTargetElementDescription(targetElementDescription)
                .withScreenshot(resultingScreenshot)
                .build();
        try (var model = getVisionModel(false)) {
            var identifiedElement = model.generateAndGetResponseAsObject(prompt,
                    "identifying the best matching UI element");
            if (identifiedElement.success()) {
                var targetLabel = identifiedElement.elementId().toLowerCase();
                return elementsToPlot.stream()
                        .filter(el -> el.id().equals(targetLabel))
                        .map(PlottedUiElement::boundingBox)
                        .map(boundingBox -> new UiElementLocationResult(true, true, boundingBox,
                                originalElements))
                        .findAny()
                        .orElseGet(() -> {
                            LOG.warn("Model returned a non-existing label '{}' for UI element with description '{}'. " +
                                            "The valid values were: {}", targetLabel, targetElementDescription,
                                    elementBoundingBoxesByLabel.keySet());
                            return new UiElementLocationResult(true, false, null, originalElements);
                        });
            } else {
                LOG.info("Model identified no element matching a description '{}'", targetElementDescription);
                return new UiElementLocationResult(true, false, null, originalElements);
            }
        }
    }

    @NotNull
    private static List<UiElementCandidate> getUiElementCandidates(BufferedImage resultingScreenshot,
                                                                   Map<Color, Rectangle> elementBoundingBoxesByLabel,
                                                                   List<PlottedUiElement> elementsToPlot) {
        drawBoundingBoxes(resultingScreenshot, elementBoundingBoxesByLabel, IS_IN_TEST_MODE);
        return elementsToPlot.stream()
                .map(el -> new UiElementCandidate(
                        el.id().toLowerCase(),
                        //BoundingBoxUtil.LabelPosition.TOP.name(),
                        getColorName(el.elementColor).toLowerCase(),
                        el.uiElement().ownDescription(),
                        el.uiElement().anchorsDescription()))
                .toList();
    }

    private static List<Rectangle> findMatchingRegions(BufferedImage wholeScreenshot, BufferedImage elementScreenshot) {
        Mat source = imdecode(new MatOfByte(imageToByteArray(wholeScreenshot, "png")), Imgcodecs.IMREAD_COLOR);
        Mat template = imdecode(new MatOfByte(imageToByteArray(elementScreenshot, "png")), Imgcodecs.IMREAD_COLOR);
        Mat result = new Mat();
        matchTemplate(source, template, result, Imgproc.TM_CCOEFF_NORMED);
        List<MatchResult> matches = new ArrayList<>();
        while (matches.size() < TOP_VISUAL_MATCHES_TO_FIND) {
            var res = Core.minMaxLoc(result);
            if (res.maxVal >= VISUAL_SIMILARITY_THRESHOLD) {
                var maxLocation = res.maxLoc;
                matches.add(new MatchResult(new Point((int) maxLocation.x, (int) maxLocation.y), res.maxVal));
                Imgproc.floodFill(result, new Mat(), maxLocation, new Scalar(0));
            } else {
                break;
            }
        }

        return matches.stream()
                .sorted(Comparator.comparingDouble(MatchResult::score).reversed())
                .limit(TOP_VISUAL_MATCHES_TO_FIND)
                .map(match ->
                        new Rectangle(match.point(), new Dimension(elementScreenshot.getWidth(), elementScreenshot.getHeight())))
                .toList();
    }

    private record MatchResult(Point point, double score) {
    }

    private record PlottedUiElement(String id, Color elementColor, UiElement uiElement, Rectangle boundingBox) {
    }

    private record UiElementLocationResult(boolean patternMatchFound, boolean visualMatchFound, Rectangle boundingBox,
                                           Collection<UiElement> candidatesUsedForLocation) {
    }
}
