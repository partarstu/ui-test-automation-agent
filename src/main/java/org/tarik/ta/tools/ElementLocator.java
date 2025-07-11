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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.dto.BoundingBox;
import org.tarik.ta.exceptions.UserChoseTerminationException;
import org.tarik.ta.exceptions.UserInterruptedExecutionException;
import org.tarik.ta.prompts.BestMatchingUiElementIdentificationPrompt.UiElementCandidate;
import org.tarik.ta.prompts.ElementDescriptionPrompt;
import org.tarik.ta.rag.RetrieverFactory;
import org.tarik.ta.prompts.BestMatchingUiElementIdentificationPrompt;
import org.tarik.ta.prompts.ElementBoundingBoxPrompt;
import org.tarik.ta.rag.model.UiElement;
import org.tarik.ta.rag.model.UiElement.Screenshot;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.rag.UiElementRetriever.RetrievedItem;
import org.tarik.ta.user_dialogs.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;


import static com.google.common.base.Preconditions.checkArgument;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Optional.*;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toConcurrentMap;
import static java.util.stream.Collectors.toMap;
import static javax.imageio.ImageIO.write;
import static javax.swing.JOptionPane.showMessageDialog;
import static org.tarik.ta.model.ModelFactory.getVisionModel;
import static org.tarik.ta.tools.ImageMatchingUtil.findMatchingRegionsWithTemplateMatching;
import static org.tarik.ta.tools.ImageMatchingUtil.findMatchingRegionsWithORB;
import static org.tarik.ta.utils.BoundingBoxUtil.*;
import static org.tarik.ta.utils.CommonUtils.captureScreen;
import static org.tarik.ta.utils.CommonUtils.getColorByName;
import static org.tarik.ta.utils.CommonUtils.getColorName;
import static org.tarik.ta.utils.CommonUtils.getScaledBoundingBox;
import static org.tarik.ta.utils.CommonUtils.sleepMillis;
import static org.tarik.ta.utils.ImageUtils.cloneImage;
import static org.tarik.ta.rag.model.UiElement.Screenshot.fromBufferedImage;

public class ElementLocator extends AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(ElementLocator.class);
    private static final double MIN_TARGET_RETRIEVAL_SCORE = AgentConfig.getElementRetrievalMinTargetScore();
    private static final double MIN_GENERAL_RETRIEVAL_SCORE = AgentConfig.getElementRetrievalMinGeneralScore();
    private static final int USER_DIALOG_DISMISS_DELAY_MILLIS = 1000;
    private static final String BOUNDING_BOX_COLOR_NAME = AgentConfig.getElementBoundingBoxColorName();
    private static final Color BOUNDING_BOX_COLOR = getColorByName(BOUNDING_BOX_COLOR_NAME);
    private static final int TOP_N_ELEMENTS_TO_RETRIEVE = AgentConfig.getRetrieverTopN();
    private static final boolean UNATTENDED_MODE = AgentConfig.isUnattendedMode();
    private static final UiElementRetriever elementRetriever = RetrieverFactory.getUiElementRetriever();
    private static final boolean IS_IN_DEBUG_MODE = AgentConfig.isTestMode();
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
    private static final String SCREENSHOTS_SAVE_FOLDER = "screens";

    public static Optional<Rectangle> locateElementOnTheScreen(String elementDescription) {
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
            if (matchingUiElements.size() > 1) {
                // TODO: Implement dialog to inform the operator that multiple elements correspond the UI element's description and that one
                //  of them needs to have the name updated plus the its description in the test step needs to be updated as well to align
                //  with each other
                LOG.warn("Multiple UI elements found in vector DB which semantically match the description '{}', but only one is expected" +
                        " in order to provide the correct visual element location results", elementDescription);
            }

            LOG.info("Found {} UI element(s) in DB corresponding to the description of '{}'. Element names: {}",
                    matchingUiElements.size(), elementDescription, matchingUiElements.stream().map(UiElement::name).toList());
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
        if (UNATTENDED_MODE) {
            LOG.warn(rootCause);
            return empty();
        } else {
            return processNoElementFoundCaseInAttendedMode(elementDescription, elementsUsed, rootCause);
        }
    }

    private static Optional<Rectangle> processNoPatternMatchesCase(String elementDescription, Collection<UiElement> elementsUsed) {
        var rootCause = ("Visual pattern matching provided no results within deadline. Either this is a bug, or most probably " +
                "the UI has been modified and the saved in DB UI element info is obsolete. The element description is: '%s'. Do " +
                "you wish to refine the UI element info or to terminate the execution ?").formatted(elementDescription);
        if (UNATTENDED_MODE) {
            LOG.warn(rootCause);
            return empty();
        } else {
            return processNoElementFoundCaseInAttendedMode(elementDescription, elementsUsed, rootCause);
        }
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

    private static UiElementLocationResult getFinalElementLocation(String elementDescription, List<UiElement> matchingUiElements) {
        BufferedImage wholeScreenshot = captureScreen();
        List<BoundingBox> identifiedByVisionBoundingBoxes = identifyBoundingBoxesUsingVision(elementDescription, wholeScreenshot);
        var featureMatchedBoundingBoxesByElementFuture = supplyAsync(() ->
                getMatchingByFeaturesRegions(matchingUiElements, wholeScreenshot));
        var templateMatchedBoundingBoxesByElementFuture = supplyAsync(() ->
                getMacthingByTemplateRegions(matchingUiElements, wholeScreenshot));

        var featureMatchedBoundingBoxesByElement = featureMatchedBoundingBoxesByElementFuture.join();
        var templateMatchedBoundingBoxesByElement = templateMatchedBoundingBoxesByElementFuture.join();

        // TODO: remove this after debug is complete
        markElementsToPlotWithBoundingBoxes(cloneImage(wholeScreenshot), getElementsToPlot(featureMatchedBoundingBoxesByElement),
                "opencv_features");
        markElementsToPlotWithBoundingBoxes(cloneImage(wholeScreenshot), getElementsToPlot(templateMatchedBoundingBoxesByElement),
                "opencv_template");

        var featureRects = featureMatchedBoundingBoxesByElement.values().stream().flatMap(List::stream).toList();
        var templateRects = templateMatchedBoundingBoxesByElement.values().stream().flatMap(List::stream).toList();
        if (identifiedByVisionBoundingBoxes.isEmpty()) {
            return chooseBestAlgorithmicMatch(elementDescription, matchingUiElements, templateRects, featureRects, wholeScreenshot,
                    featureMatchedBoundingBoxesByElement);
        } else {
            return chooseBestHybridMatch(elementDescription, matchingUiElements, identifiedByVisionBoundingBoxes, wholeScreenshot,
                    featureRects, templateRects)
                    .orElseGet(() -> {
                        LOG.info("Found no common regions between vision model and any of algorithmic regions, falling back to " +
                                "selecting the best algorithmic one");
                        return chooseBestAlgorithmicMatch(elementDescription, matchingUiElements, templateRects, featureRects,
                                wholeScreenshot, featureMatchedBoundingBoxesByElement);
                    });
        }
    }

    @NotNull
    private static Optional<UiElementLocationResult> chooseBestHybridMatch(String elementDescription, List<UiElement> matchingUiElements,
                                                                           List<BoundingBox> identifiedByVisionBoundingBoxes,
                                                                           BufferedImage wholeScreenshot, List<Rectangle> featureRects,
                                                                           List<Rectangle> templateRects) {
        LOG.info("Mapping provided by vision model results to the algorithmic ones");
        var visionIdentifiedBoxes = identifiedByVisionBoundingBoxes.stream()
                .map(bb -> bb.getActualBoundingBox(wholeScreenshot.getWidth(), wholeScreenshot.getHeight()))
                .toList();
        List<Rectangle> visionAndFeatureIntersections = getIntersections(visionIdentifiedBoxes, featureRects);
        List<Rectangle> visionAndTemplateIntersections = getIntersections(visionIdentifiedBoxes, templateRects);
        List<Rectangle> bestIntersections = getIntersections(visionAndFeatureIntersections, visionAndTemplateIntersections);

        if (!bestIntersections.isEmpty()) {
            if (bestIntersections.size() > 1) {
                LOG.info("Found {} common vision model and algorithmic regions, using them for further refinement by " +
                        "the model.", bestIntersections.size());
                return of(chooseBestVisualMatch(elementDescription, Map.of(matchingUiElements.getFirst(), bestIntersections),
                        wholeScreenshot, "intersection_feature_and_template"));
            } else {
                LOG.info("Found a single common vision model and common algorithmic region, returning it");
                return of(new UiElementLocationResult(true, true, bestIntersections.getFirst(), matchingUiElements));
            }
        } else {
            var goodIntersections = Stream.of(visionAndFeatureIntersections.stream(), visionAndTemplateIntersections.stream())
                    .flatMap(Stream::distinct)
                    .toList();
            if (!goodIntersections.isEmpty()) {
                if (goodIntersections.size() > 1) {
                    LOG.info("Found {} common vision model and either template or feature matching algorithms, using them for " +
                            "further refinement by the model.", goodIntersections.size());
                    return of(chooseBestVisualMatch(elementDescription, Map.of(matchingUiElements.getFirst(), goodIntersections),
                            wholeScreenshot, "intersection_feature_and_template"));
                } else {
                    LOG.info("Found a single common region between vision model and one of algorithmic regions, returning it");
                    return of(new UiElementLocationResult(true, true, goodIntersections.getFirst(), matchingUiElements));
                }
            } else {
                return empty();
            }
        }
    }

    private static UiElementLocationResult chooseBestAlgorithmicMatch(String elementDescription, List<UiElement> matchingUiElements,
                                                                      List<Rectangle> templateRects, List<Rectangle> featureRects,
                                                                      BufferedImage wholeScreenshot,
                                                                      ConcurrentMap<UiElement, List<Rectangle>> featureMatchedBoundingBoxesByElement) {
        LOG.info("Vision model provided no detection results, proceeding with algorithmic matches");
        var algorithmicMatches = getIntersections(templateRects, featureRects);
        if (!algorithmicMatches.isEmpty()) {
            LOG.info("Found {} common detection regions between algorithmic matches, using them for further refinement by the " +
                    "model.", algorithmicMatches.size());
            return chooseBestVisualMatch(elementDescription, Map.of(matchingUiElements.getFirst(), algorithmicMatches),
                    wholeScreenshot, "intersection_feature_and_template");
        } else {
            LOG.info("Found no common detection regions between algorithmic matches, using all originally detected regions for " +
                    "further refinement by the model.");
            Map<UiElement, List<Rectangle>> combinedBoundingBoxes = new HashMap<>();
            featureMatchedBoundingBoxesByElement.forEach((uiElement, boxes) -> {
                combinedBoundingBoxes.putIfAbsent(uiElement, new LinkedList<>());
                combinedBoundingBoxes.merge(uiElement, boxes, (existing, newOnes) -> {
                    existing.addAll(newOnes);
                    return existing;
                });
            });
            return chooseBestVisualMatch(elementDescription, combinedBoundingBoxes, wholeScreenshot,
                    "all_feature_and_template");
        }
    }

    @NotNull
    private static ConcurrentMap<UiElement, List<Rectangle>> getMacthingByTemplateRegions(List<UiElement> matchingUiElements,
                                                                                          BufferedImage wholeScreenshot) {
        return matchingUiElements.stream()
                .collect(toConcurrentMap(uiElement -> uiElement, uiElement -> {
                    var elementScreenshot = uiElement.screenshot().toBufferedImage();
                    return mergeOverlappingRectangles(findMatchingRegionsWithTemplateMatching(wholeScreenshot,
                            elementScreenshot));
                }));
    }

    @NotNull
    private static ConcurrentMap<UiElement, List<Rectangle>> getMatchingByFeaturesRegions(List<UiElement> matchingUiElements,
                                                                                          BufferedImage wholeScreenshot) {
        return matchingUiElements.stream()
                .collect(toConcurrentMap(uiElement -> uiElement, uiElement -> {
                    var elementScreenshot = uiElement.screenshot().toBufferedImage();
                    return findMatchingRegionsWithORB(wholeScreenshot, elementScreenshot);
                }));
    }

    private static List<BoundingBox> identifyBoundingBoxesUsingVision(String elementDescription, BufferedImage wholeScreenshot) {
        var elementBoundingBoxPrompt = ElementBoundingBoxPrompt.builder()
                .withElementDescription(elementDescription)
                .withScreenshot(wholeScreenshot)
                .build();
        List<BoundingBox> identifiedByVisionBoundingBoxes;
        try (var model = getVisionModel(true)) {
            var identifiedBoundingBoxFuture = supplyAsync(() ->
                    model.generateAndGetResponseAsObject(elementBoundingBoxPrompt, "getting bounding box from vision model"));
            identifiedByVisionBoundingBoxes = identifiedBoundingBoxFuture.join().boundingBoxes();
            LOG.info("Model has identified bounding boxes : {}.", identifiedByVisionBoundingBoxes);
            if (IS_IN_DEBUG_MODE && !identifiedByVisionBoundingBoxes.isEmpty()) {
                var resultingScreenshot = cloneImage(wholeScreenshot);
                identifiedByVisionBoundingBoxes.forEach(identifiedBoundingBox -> {
                    drawBoundingBox(resultingScreenshot, identifiedBoundingBox.getActualBoundingBox(wholeScreenshot.getWidth(),
                            wholeScreenshot.getHeight()), BOUNDING_BOX_COLOR);
                });
                saveScreenshot(resultingScreenshot, "vision");
            }
        }
        return identifiedByVisionBoundingBoxes;
    }

    @NotNull
    private static List<Rectangle> getIntersections(List<Rectangle> firstSet, List<Rectangle> secondSet) {
        return firstSet.stream()
                .flatMap(r1 -> secondSet.stream()
                        .map(r1::intersection)
                        .filter(r -> !r.isEmpty()))
                .toList();
    }

    private static UiElementLocationResult chooseBestVisualMatch(String elementDescription,
                                                                 Map<UiElement, List<Rectangle>> matchedBoundingBoxesByElement,
                                                                 BufferedImage wholeScreenshot, String matchAlgorithm) {
        List<UiElement> matchingUiElements = matchedBoundingBoxesByElement.keySet().stream().toList();
        int visualMatchesAmount = matchedBoundingBoxesByElement.values().stream().mapToInt(Collection::size).sum();
        var elementNames = matchedBoundingBoxesByElement.keySet().stream().map(UiElement::name).collect(joining("', '", "'", "'"));
        if (visualMatchesAmount < 1) {
            LOG.warn("No matching regions found for the following UI elements: {}", elementNames);
            return new UiElementLocationResult(false, false, null, matchingUiElements);
        } else {
            LOG.info("Found {} visual match(es) corresponding to '{}'. Name(s): {}. Choosing the best one.",
                    visualMatchesAmount, elementDescription, elementNames);
            return getFinalUiElementLocationUsingModel(matchedBoundingBoxesByElement, wholeScreenshot, elementDescription, matchAlgorithm);
        }
    }

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
            BufferedImage screenshot, String targetElementDescription, String matchAlgorithm) {
        // First all elements must be plotted onto the screenshot with unique label so that the model knows which ID corresponds to
        // which UI element. We use available colors as unique labels
        var originalElements = matchedBoundingBoxesByElement.keySet();
        var boxedAmount = matchedBoundingBoxesByElement.values().stream().mapToInt(List::size).sum();
        checkArgument(boxedAmount <= availableBoundingBoxColors.size(), "Amount of bounding boxes to plot exceeds the amount " +
                "of available colors to use as labels for disambiguation. Please increase the amount of available colors in the list");
        var elementsToPlot = getElementsToPlot(matchedBoundingBoxesByElement);
        var resultingScreenshot = cloneImage(screenshot);

        // Now asking the model to identify the element which is the best fit to the target description
        markElementsToPlotWithBoundingBoxes(resultingScreenshot, elementsToPlot, matchAlgorithm);
        var uiElementCandidates = elementsToPlot.stream()
                .map(el -> new UiElementCandidate(
                        el.id().toLowerCase(),
                        getColorName(el.elementColor).toLowerCase(),
                        el.uiElement().ownDescription(),
                        el.uiElement().anchorsDescription()))
                .toList();
        LOG.info("Candidates: {}", uiElementCandidates);
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
                            LOG.warn("Model returned a non-existing color label '{}' for UI element with description '{}'. " +
                                            "The valid values were: {}", targetLabel, targetElementDescription,
                                    elementsToPlot.stream().map(PlottedUiElement::elementColor).toList());
                            return new UiElementLocationResult(true, false, null, originalElements);
                        });
            } else {
                LOG.info("Model identified no element matching a description '{}'", targetElementDescription);
                return new UiElementLocationResult(true, false, null, originalElements);
            }
        }
    }

    @NotNull
    private static List<PlottedUiElement> getElementsToPlot(Map<UiElement, List<Rectangle>> matchedBoundingBoxesByElement) {
        var colorsToUse = new LinkedList<>(availableBoundingBoxColors);
        AtomicInteger colorCounter = new AtomicInteger();
        return matchedBoundingBoxesByElement.entrySet().stream()
                .flatMap(boxesByElement -> boxesByElement.getValue().stream()
                        .map(box -> new PlottedUiElement(String.valueOf(colorCounter.incrementAndGet()),
                                colorsToUse.removeFirst(), boxesByElement.getKey(), box)))
                .toList();
    }

    private static void markElementsToPlotWithBoundingBoxes(BufferedImage resultingScreenshot, List<PlottedUiElement> elementsToPlot,
                                                            String postfix) {
        Map<Color, Rectangle> elementBoundingBoxesByLabel = elementsToPlot.stream()
                .collect(toMap(PlottedUiElement::elementColor, PlottedUiElement::boundingBox));
        drawBoundingBoxes(resultingScreenshot, elementBoundingBoxesByLabel);
        if (IS_IN_DEBUG_MODE) {
            saveScreenshot(resultingScreenshot, postfix);
        }
    }

    private static void saveScreenshot(BufferedImage resultingScreenshot, String postfix) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = ofPattern("yyyy_MM_dd_HH_mm_ss");
        String timestamp = now.format(formatter);
        var filePath = Paths.get(SCREENSHOTS_SAVE_FOLDER)
                .resolve("%s_%s.png".formatted(timestamp, postfix)).toAbsolutePath();
        try {
            write(resultingScreenshot, "png", filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record PlottedUiElement(String id, Color elementColor, UiElement uiElement, Rectangle boundingBox) {
    }

    private record UiElementLocationResult(boolean patternMatchFound, boolean visualMatchFound, Rectangle boundingBox,
                                           Collection<UiElement> candidatesUsedForLocation) {
    }
}