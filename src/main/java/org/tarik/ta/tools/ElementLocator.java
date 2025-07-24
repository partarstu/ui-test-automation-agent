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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;
import org.tarik.ta.dto.BoundingBox;
import org.tarik.ta.dto.UiElementIdentificationResult;
import org.tarik.ta.exceptions.UserChoseTerminationException;
import org.tarik.ta.exceptions.UserInterruptedExecutionException;
import org.tarik.ta.prompts.ElementDescriptionPrompt;
import org.tarik.ta.prompts.PageDescriptionPrompt;
import org.tarik.ta.rag.RetrieverFactory;
import org.tarik.ta.prompts.BestMatchingUiElementIdentificationPrompt;
import org.tarik.ta.prompts.ElementBoundingBoxPrompt;
import org.tarik.ta.rag.model.UiElement;
import org.tarik.ta.rag.model.UiElement.Screenshot;
import org.tarik.ta.rag.UiElementRetriever;
import org.tarik.ta.rag.UiElementRetriever.RetrievedUiElementItem;
import org.tarik.ta.user_dialogs.*;
import org.tarik.ta.utils.CommonUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Thread.currentThread;
import static java.util.Collections.max;
import static java.util.Comparator.comparingDouble;
import static java.util.Optional.*;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;
import static java.util.stream.IntStream.range;
import static java.util.stream.Stream.concat;
import static javax.swing.JOptionPane.showMessageDialog;
import static org.tarik.ta.model.ModelFactory.getVisionModel;
import static org.tarik.ta.utils.CommonUtils.*;
import static org.tarik.ta.utils.ImageMatchingUtil.findMatchingRegionsWithTemplateMatching;
import static org.tarik.ta.utils.ImageMatchingUtil.findMatchingRegionsWithORB;
import static org.tarik.ta.utils.BoundingBoxUtil.*;
import static org.tarik.ta.utils.ImageUtils.cloneImage;
import static org.tarik.ta.rag.model.UiElement.Screenshot.fromBufferedImage;
import static org.tarik.ta.utils.ImageUtils.saveScreenshot;

public class ElementLocator extends AbstractTools {
    private static final Logger LOG = LoggerFactory.getLogger(ElementLocator.class);
    private static final double MIN_TARGET_RETRIEVAL_SCORE = AgentConfig.getElementRetrievalMinTargetScore();
    private static final double MIN_PAGE_RELEVANCE_SCORE = AgentConfig.getElementRetrievalMinPageRelevanceScore();
    private static final double MIN_GENERAL_RETRIEVAL_SCORE = AgentConfig.getElementRetrievalMinGeneralScore();
    private static final double MIN_INTERSECTION_PERCENTAGE = AgentConfig.getElementLocatorMinIntersectionPercentage();
    private static final int USER_DIALOG_DISMISS_DELAY_MILLIS = 1000;
    private static final String BOUNDING_BOX_COLOR_NAME = AgentConfig.getElementBoundingBoxColorName();
    private static final Color BOUNDING_BOX_COLOR = getColorByName(BOUNDING_BOX_COLOR_NAME);
    private static final int TOP_N_ELEMENTS_TO_RETRIEVE = AgentConfig.getRetrieverTopN();
    private static final boolean UNATTENDED_MODE = AgentConfig.isUnattendedMode();
    private static final UiElementRetriever elementRetriever = RetrieverFactory.getUiElementRetriever();
    private static final boolean DEBUG_MODE = AgentConfig.isDebugMode();
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
    private static final int MODEL_VOTE_COUNT = 7;

    public static Optional<Rectangle> locateElementOnTheScreen(String elementDescription) {
        var retrievedElements = elementRetriever.retrieveUiElements(elementDescription, TOP_N_ELEMENTS_TO_RETRIEVE,
                MIN_GENERAL_RETRIEVAL_SCORE);
        var matchingByDescriptionUiElements = retrievedElements.stream()
                .filter(retrievedUiElementItem -> retrievedUiElementItem.mainScore() >= MIN_TARGET_RETRIEVAL_SCORE)
                .map(RetrievedUiElementItem::element)
                .toList();
        if (matchingByDescriptionUiElements.isEmpty() && !retrievedElements.isEmpty()) {
            return processNoElementsFoundInDbWithSimilarCandidatesPresentCase(elementDescription, retrievedElements);
        } else if (matchingByDescriptionUiElements.isEmpty()) {
            return processNoElementsFoundInDbCase(elementDescription);
        } else {
            UiElement bestMatchingElement;
            if (matchingByDescriptionUiElements.size() > 1) {
                LOG.info("{} UI elements found in vector DB which semantically match the description '{}'. Scoring them based on " +
                        "the relevance to the currently opened page.", matchingByDescriptionUiElements.size(), elementDescription);
                var bestMatchingByDescriptionAndPageRelevanceUiElements =
                        getBestMatchingByDescriptionAndPageRelevanceUiElements(elementDescription);
                if (bestMatchingByDescriptionAndPageRelevanceUiElements.size() > 1) {
                    return processMultipleElementsRelevantToPageFoundCase(elementDescription,
                            bestMatchingByDescriptionAndPageRelevanceUiElements, retrievedElements);
                } else if (bestMatchingByDescriptionAndPageRelevanceUiElements.isEmpty()) {
                    return processNoElementsRelevantToPageFoundCase(elementDescription, matchingByDescriptionUiElements);
                } else {
                    bestMatchingElement = bestMatchingByDescriptionAndPageRelevanceUiElements.getFirst();
                }
            } else {
                bestMatchingElement = matchingByDescriptionUiElements.getFirst();
            }

            LOG.info("Found {} UI element(s) in DB corresponding to the description of '{}'. Element names: {}",
                    matchingByDescriptionUiElements.size(), elementDescription,
                    matchingByDescriptionUiElements.stream().map(UiElement::name).toList());
            return findElementAndProcessLocationResult(() -> getFinalElementLocation(bestMatchingElement), elementDescription);
        }
    }

    @NotNull
    private static List<UiElement> getBestMatchingByDescriptionAndPageRelevanceUiElements(String elementDescription) {
        String pageDescription = getPageDescriptionFromModel();
        var retrievedWithPageRelevanceScoreElements = elementRetriever.retrieveUiElements(elementDescription,
                pageDescription, TOP_N_ELEMENTS_TO_RETRIEVE, MIN_GENERAL_RETRIEVAL_SCORE);
        return retrievedWithPageRelevanceScoreElements.stream()
                .filter(retrievedUiElementItem -> retrievedUiElementItem.pageRelevanceScore() >= MIN_PAGE_RELEVANCE_SCORE)
                .map(RetrievedUiElementItem::element)
                .toList();
    }

    private static Optional<Rectangle> processMultipleElementsRelevantToPageFoundCase(String elementDescription,
                                                                                      List<UiElement> bestMatchingByPageRelevanceUiElements,
                                                                                      List<RetrievedUiElementItem> retrievedElements) {
        if (UNATTENDED_MODE) {
            var message = ("Found not a single, but %d UI elements in DB which correspond to '%s' " +
                    "and all have the minimum required page relevance score. Please refine them using attended " +
                    "mode.").formatted(bestMatchingByPageRelevanceUiElements.size(), elementDescription);
            throw new IllegalStateException(message);
        } else {
            var reasonToRefine = ("I have found more than one UI element in my Database which have minimum page " +
                    "relevance score and match the description '%s'").formatted(elementDescription);
            promptUserToRefinePossibleCandidateUiElements(retrievedElements, reasonToRefine);
            return promptUserForNextAction(elementDescription);
        }
    }

    private static Optional<Rectangle> processNoElementsRelevantToPageFoundCase(String elementDescription,
                                                                                List<UiElement> originallyFoundUiElements) {
        if (UNATTENDED_MODE) {
            var message = ("No matching elements by page relevance found, but there were %s " +
                    "UI elements matching the description '%s' initially. Please lower the page relevance threshold or refine them using " +
                    "attended mode.")
                    .formatted(originallyFoundUiElements.size(), elementDescription);
            throw new IllegalStateException(message);
        } else {
            var reasonToRefine = ("I have found no UI elements in my Database which have the minimum page " +
                    "relevance score and match the description '%s'. However, I have found %d UI elements matching this description " +
                    "without taking into account their page relevance. If the target element is in this list, you could update its " +
                    "description and anchors information to correspond better to the page/view where its located.")
                    .formatted(elementDescription, originallyFoundUiElements.size());
            promptUserToRefineUiElements(reasonToRefine, originallyFoundUiElements);
            return promptUserForNextAction(elementDescription);
        }
    }


    private static Optional<Rectangle> processNoElementsFoundInDbWithSimilarCandidatesPresentCase(String elementDescription,
                                                                                                  List<RetrievedUiElementItem> retrievedElements) {
        if (UNATTENDED_MODE) {
            var retrievedElementsString = retrievedElements.stream()
                    .map(el -> "%s --> %.1f".formatted(el.element().name(), el.mainScore()))
                    .collect(joining(", "));
            LOG.warn("No UI elements found in vector DB which semantically match the description '{}' with the " +
                            "similarity mainScore > {}. The most similar element names by similarity mainScore are: {}", elementDescription,
                    "%.1f".formatted(MIN_TARGET_RETRIEVAL_SCORE), retrievedElementsString);
            return empty();
        } else {
            // This one happens as soon as DB has some elements, but none of them has the similarity higher than the configured threshold
            var reasonToRefine = "I haven't found any UI elements in my Database which perfectly match the description '%s'"
                    .formatted(elementDescription);
            promptUserToRefinePossibleCandidateUiElements(retrievedElements, reasonToRefine);
            return promptUserForNextAction(elementDescription);
        }
    }


    @NotNull
    private static Optional<Rectangle> processNoElementsFoundInDbCase(String elementDescription) {
        if (UNATTENDED_MODE) {
            LOG.warn("No UI elements found in vector DB which semantically match the description '{}' with the " +
                    "similarity mainScore > {}.", elementDescription, "%.1f".formatted(MIN_GENERAL_RETRIEVAL_SCORE));
            return empty();
        } else {
            // This one will be seldom, because after at least some elements are in DB, they will be displayed
            NewElementInfoNeededPopup.display(elementDescription);
            return of(promptUserForCreatingNewElement(elementDescription));
        }
    }

    private static String getPageDescriptionFromModel() {
        var pageDescriptionPrompt = PageDescriptionPrompt.builder()
                .withScreenshot(captureScreen())
                .build();
        try (var model = getVisionModel(false)) {
            var pageDescriptionResult = model.generateAndGetResponseAsObject(pageDescriptionPrompt,
                    "generating the description of the page relative to the element");
            return pageDescriptionResult.pageDescription();
        }
    }

    private static void promptUserToRefinePossibleCandidateUiElements(List<RetrievedUiElementItem> retrievedElements,
                                                                      String refinementReason) {
        List<UiElement> elementsToRefine = retrievedElements.stream()
                .map(RetrievedUiElementItem::element)
                .toList();
        var message = ("'%s'. You could update or delete the following ones in order to have " +
                "more adequate search results next time:").formatted(refinementReason);
        promptUserToRefineUiElements(message, elementsToRefine);
    }

    private static void promptUserToRefineUiElements(String message, List<UiElement> elementsToRefine) {
        Function<UiElement, UiElement> elementUpdater = element -> {
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
        };

        Consumer<UiElement> elementRemover = element -> {
            try {
                elementRetriever.removeElement(element);
            } catch (Exception e) {
                var logMessage = "Couldn't delete the following UI element: " + element;
                LOG.error(logMessage, e);
                showMessageDialog(null, "Couldn't delete the UI element, see the logs for details");
            }
        };

        UiElementRefinementPopup.display(message, elementsToRefine, elementUpdater, elementRemover);
    }

    private static Optional<Rectangle> findElementAndProcessLocationResult(Supplier<UiElementLocationResult> resultSupplier,
                                                                           String elementDescription) {
        var locationResult = resultSupplier.get();
        return switch (locationResult) {
            case UiElementLocationResult(boolean patternMatch, var _, var _, var elementUsed) when !patternMatch ->
                    processNoPatternMatchesCase(elementDescription, elementUsed);
            case UiElementLocationResult(var _, boolean visualMatchByModel, var _, var elementUsed) when
                    !visualMatchByModel -> processNoVisualMatchCase(elementDescription, elementUsed);
            case UiElementLocationResult(var _, var _, Rectangle boundingBox, var _) when boundingBox != null -> {
                LOG.info("The best visual match for the description '{}' has been located at: {}", elementDescription, boundingBox);
                yield of(getScaledBoundingBox(boundingBox));
            }
            default -> throw new IllegalStateException("Got element location result in unexpected state: " + locationResult);
        };
    }

    private static Optional<Rectangle> processNoVisualMatchCase(String elementDescription, UiElement elementUsed) {
        var rootCause = ("Visual pattern matching provided results, but the model has decided that none of them visually " +
                "matches the description '%s'. Either this is a bug, or the UI has been modified and the saved in DB UI element " +
                "info is obsolete. Do you wish to refine the UI element info or to terminate the execution ?")
                .formatted(elementDescription);
        if (UNATTENDED_MODE) {
            LOG.warn(rootCause);
            return empty();
        } else {
            return processNoElementFoundCaseInAttendedMode(elementDescription, elementUsed, rootCause);
        }
    }

    private static Optional<Rectangle> processNoPatternMatchesCase(String elementDescription, UiElement elementUsed) {
        var rootCause = ("Visual pattern matching provided no results within deadline. Either this is a bug, or most probably " +
                "the UI has been modified and the saved in DB UI element info is obsolete. The element description is: '%s'. Do " +
                "you wish to refine the UI element info or to terminate the execution ?").formatted(elementDescription);
        if (UNATTENDED_MODE) {
            LOG.warn(rootCause);
            return empty();
        } else {
            return processNoElementFoundCaseInAttendedMode(elementDescription, elementUsed, rootCause);
        }
    }

    private static Optional<Rectangle> processNoElementFoundCaseInAttendedMode(String elementDescription, @NotNull UiElement elementUsed,
                                                                               String rootCause) {
        return switch (NoElementFoundPopup.displayAndGetUserDecision(rootCause)) {
            case CONTINUE -> {
                var message = "You could update or delete the element which was used in the search in order to have " +
                        "more adequate search results next time:";
                promptUserToRefineUiElements(message, List.of(elementUsed));
                yield promptUserForNextAction(elementDescription);
            }
            case TERMINATE -> {
                logUserTerminationRequest();
                throw new UserChoseTerminationException();
            }
        };
    }

    private static Optional<Rectangle> promptUserForNextAction(String elementDescription) {
        return switch (NextActionPopup.displayAndGetUserDecision()) {
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

    private static UiElementLocationResult getFinalElementLocation(UiElement elementRetrievedFromMemory) {
        var elementScreenshot = elementRetrievedFromMemory.screenshot().toBufferedImage();
        double originalElementArea = elementScreenshot.getHeight() * elementScreenshot.getWidth();
        BufferedImage wholeScreenshot = captureScreen();
        List<BoundingBox> identifiedByVisionBoundingBoxes = identifyBoundingBoxesUsingVision(elementRetrievedFromMemory, wholeScreenshot);
        var featureMatchedBoundingBoxesByElementFuture = supplyAsync(() ->
                findMatchingRegionsWithORB(wholeScreenshot, elementScreenshot));
        var templateMatchedBoundingBoxesByElementFuture = supplyAsync(() ->
                mergeOverlappingRectangles(findMatchingRegionsWithTemplateMatching(wholeScreenshot, elementScreenshot)));

        var featureMatchedBoundingBoxes = featureMatchedBoundingBoxesByElementFuture.join();
        var templateMatchedBoundingBoxes = templateMatchedBoundingBoxesByElementFuture.join();

        if (DEBUG_MODE) {
            markElementsToPlotWithBoundingBoxes(cloneImage(wholeScreenshot),
                    getElementToPlot(elementRetrievedFromMemory, featureMatchedBoundingBoxes), "opencv_features_original");
            markElementsToPlotWithBoundingBoxes(cloneImage(wholeScreenshot),
                    getElementToPlot(elementRetrievedFromMemory, templateMatchedBoundingBoxes), "opencv_template_original");
            var visionIdentifiedBoxes = getScaledBoundingBoxes(identifiedByVisionBoundingBoxes, wholeScreenshot);
            var image = cloneImage(wholeScreenshot);
            visionIdentifiedBoxes.forEach(box -> drawBoundingBox(image, box, BOUNDING_BOX_COLOR));
            saveScreenshot(image, "vision_original");
        }

        if (identifiedByVisionBoundingBoxes.isEmpty() && featureMatchedBoundingBoxes.isEmpty() &&
                templateMatchedBoundingBoxes.isEmpty()) {
            return new UiElementLocationResult(false, false, null, elementRetrievedFromMemory);
        } else if (identifiedByVisionBoundingBoxes.isEmpty()) {
            LOG.info("Vision model provided no detection results, proceeding with algorithmic matches");
            return chooseBestAlgorithmicMatch(elementRetrievedFromMemory, wholeScreenshot,
                    featureMatchedBoundingBoxes, templateMatchedBoundingBoxes, originalElementArea);
        } else {
            // Relying purely on bounding box identification of the vision model is a bad idea, that's why if only those results are
            // present, we'll send these to the model once more to confirm, even if there's only a single result.
            if (featureMatchedBoundingBoxes.isEmpty() && templateMatchedBoundingBoxes.isEmpty()) {
                var visionIdentifiedBoxes = getScaledBoundingBoxes(identifiedByVisionBoundingBoxes, wholeScreenshot);
                return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, visionIdentifiedBoxes, wholeScreenshot,
                        "vision_only");
            } else {
                return chooseBestCommonMatch(elementRetrievedFromMemory, identifiedByVisionBoundingBoxes, wholeScreenshot,
                        featureMatchedBoundingBoxes, templateMatchedBoundingBoxes, originalElementArea)
                        .orElseGet(() -> {
                            var visionIdentifiedBoxes = getScaledBoundingBoxes(identifiedByVisionBoundingBoxes, wholeScreenshot);
                            var algorithmicIntersections = getIntersections(featureMatchedBoundingBoxes,
                                    templateMatchedBoundingBoxes, originalElementArea);
                            if (!algorithmicIntersections.isEmpty()) {
                                var boxes = concat(visionIdentifiedBoxes.stream(), algorithmicIntersections.stream()).toList();
                                return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, boxes, wholeScreenshot,
                                        "vision_and_algorithmic_only_intersections");
                            } else {
                                var boxes = Stream.of(visionIdentifiedBoxes, featureMatchedBoundingBoxes, templateMatchedBoundingBoxes)
                                        .flatMap(Collection::stream)
                                        .toList();
                                return selectBestMatchingUiElementUsingModel(elementRetrievedFromMemory, boxes, wholeScreenshot,
                                        "vision_and_algorithmic_regions_separately");
                            }
                        });
            }
        }
    }

    @NotNull
    private static Optional<UiElementLocationResult> chooseBestCommonMatch(UiElement matchingUiElement,
                                                                           List<BoundingBox> identifiedByVisionBoundingBoxes,
                                                                           BufferedImage wholeScreenshot, List<Rectangle> featureRects,
                                                                           List<Rectangle> templateRects, double originalElementArea) {
        LOG.info("Mapping provided by vision model results to the algorithmic ones");
        var visionIdentifiedBoxes = getScaledBoundingBoxes(identifiedByVisionBoundingBoxes, wholeScreenshot);
        var visionAndFeatureIntersections = getIntersections(visionIdentifiedBoxes, featureRects, originalElementArea);
        var visionAndTemplateIntersections = getIntersections(visionIdentifiedBoxes, templateRects, originalElementArea);
        var bestIntersections = getIntersections(visionAndFeatureIntersections, visionAndTemplateIntersections, originalElementArea);

        if (!bestIntersections.isEmpty()) {
            if (bestIntersections.size() > 1) {
                LOG.info("Found {} common vision model and algorithmic regions, using them for further refinement by " +
                        "the model.", bestIntersections.size());
                return of(selectBestMatchingUiElementUsingModel(matchingUiElement, bestIntersections, wholeScreenshot, "intersection_all"));
            } else {
                LOG.info("Found a single common vision model and common algorithmic region, returning it");
                return of(new UiElementLocationResult(true, true, bestIntersections.getFirst(), matchingUiElement));
            }
        } else {
            var goodIntersections = Stream.of(visionAndFeatureIntersections.stream(), visionAndTemplateIntersections.stream())
                    .flatMap(Stream::distinct)
                    .toList();
            if (!goodIntersections.isEmpty()) {
                LOG.info("Found {} common regions between vision model and either template or feature matching algorithms, " +
                        "using them for further refinement by the model.", goodIntersections.size());
                return of(selectBestMatchingUiElementUsingModel(matchingUiElement, goodIntersections, wholeScreenshot,
                        "intersection_vision_and_one_algorithm"));
            } else {
                LOG.info("Found no common regions between vision model and either template or feature matching algorithms");
                return empty();
            }
        }
    }

    @NotNull
    private static List<Rectangle> getScaledBoundingBoxes(List<BoundingBox> identifiedByVisionBoundingBoxes,
                                                          BufferedImage wholeScreenshot) {
        return identifiedByVisionBoundingBoxes.stream()
                .map(bb -> bb.getActualBoundingBox(wholeScreenshot.getWidth(), wholeScreenshot.getHeight()))
                .toList();
    }

    private static UiElementLocationResult chooseBestAlgorithmicMatch(UiElement matchingUiElement, BufferedImage wholeScreenshot,
                                                                      List<Rectangle> featureMatchedBoxes,
                                                                      List<Rectangle> templateMatchedBoxes,
                                                                      double originalElementArea) {
        if (templateMatchedBoxes.isEmpty() && featureMatchedBoxes.isEmpty()) {
            LOG.info("No algorithmic matches provided for selection");
            return new UiElementLocationResult(false, false, null, matchingUiElement);
        }

        var algorithmicIntersections = getIntersections(templateMatchedBoxes, featureMatchedBoxes, originalElementArea);
        if (!algorithmicIntersections.isEmpty()) {
            LOG.info("Found {} common detection regions between algorithmic matches, using them for further refinement by the " +
                    "model.", algorithmicIntersections.size());
            return selectBestMatchingUiElementUsingModel(matchingUiElement, algorithmicIntersections, wholeScreenshot,
                    "intersection_feature_and_template");
        } else {
            LOG.info("Found no common detection regions between algorithmic matches, using all originally detected regions for " +
                    "further refinement by the model.");
            var combinedBoundingBoxes = concat(featureMatchedBoxes.stream(), templateMatchedBoxes.stream()).toList();
            return selectBestMatchingUiElementUsingModel(matchingUiElement, combinedBoundingBoxes, wholeScreenshot,
                    "all_feature_and_template");
        }
    }

    private static List<BoundingBox> identifyBoundingBoxesUsingVision(UiElement element, BufferedImage wholeScreenshot) {
        LOG.info("Asking model to identify bounding boxes for each element which looks like '{}'.", element.name());
        var elementBoundingBoxPrompt = ElementBoundingBoxPrompt.builder()
                .withUiElement(element)
                .withScreenshot(wholeScreenshot)
                .build();
        List<BoundingBox> identifiedByVisionBoundingBoxes;
        try (var model = getVisionModel(true)) {
            var identifiedBoundingBoxFuture = supplyAsync(() ->
                    model.generateAndGetResponseAsObject(elementBoundingBoxPrompt, "getting bounding boxes from vision model"));
            identifiedByVisionBoundingBoxes = identifiedBoundingBoxFuture.join().boundingBoxes();
            LOG.info("Model has identified bounding boxes : {}.", identifiedByVisionBoundingBoxes);
        }
        return identifiedByVisionBoundingBoxes;
    }

    @NotNull
    private static List<Rectangle> getIntersections(List<Rectangle> firstSet, List<Rectangle> secondSet, double originalElementArea) {
        return firstSet.stream()
                .flatMap(r1 -> secondSet.stream()
                        .map(r1::intersection)
                        .filter(intersection -> !intersection.isEmpty())
                        .filter(intersection -> {
                            double intersectionArea = intersection.getWidth() * intersection.getHeight();
                            return (intersectionArea / originalElementArea) >= MIN_INTERSECTION_PERCENTAGE;
                        }))
                .toList();
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
                    uiElementDescriptionResult.ownDescription(), uiElementDescriptionResult.anchorsDescription(),
                    uiElementDescriptionResult.pageSummary(), null);
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
                uiElement.anchorsDescription(), uiElement.pageSummary(), screenshot);
        elementRetriever.storeElement(uiElementToStore);
    }

    private static UiElementLocationResult selectBestMatchingUiElementUsingModel(UiElement uiElement,
                                                                                 List<Rectangle> matchedBoundingBoxes,
                                                                                 BufferedImage screenshot, String matchAlgorithm) {
        // TODO: Implement here an alternative algorithm to label bounding boxes, e.g. using unique IDs, if there's not enough colors for
        //  all elements

        // First all elements must be plotted onto the screenshot with unique label so that the model knows which ID corresponds to
        // which UI element. We use available colors as unique labels
        var boxedAmount = matchedBoundingBoxes.size();
        checkArgument(boxedAmount > 0, "Amount of bounding boxes to plot must be > 0");
        checkArgument(boxedAmount <= availableBoundingBoxColors.size(), "Amount of bounding boxes to plot exceeds the amount " +
                "of available colors to use as labels for disambiguation. Please increase the amount of available colors in the list");
        var colorsToUse = new LinkedList<>(availableBoundingBoxColors);
        var boxesWithColors = matchedBoundingBoxes.stream()
                .collect(toMap(_ -> colorsToUse.removeFirst(), identity()));
        var resultingScreenshot = cloneImage(screenshot);
        drawBoundingBoxes(resultingScreenshot, boxesWithColors);
        if (DEBUG_MODE) {
            saveScreenshot(resultingScreenshot, "model_selection_%s".formatted(matchAlgorithm));
        }

        var successfulIdentificationResults =
                getValidSuccessfulIdentificationResultsFromModelUsingQuorum(uiElement, resultingScreenshot, boxesWithColors);
        if (successfulIdentificationResults.size() <= MODEL_VOTE_COUNT / 2) {
            LOG.warn("Couldn't identify the element '{}' by majority vote. {} successful votes out of {}.",
                    uiElement.name(), successfulIdentificationResults.size(), MODEL_VOTE_COUNT);
            return new UiElementLocationResult(true, false, null, uiElement);
        }

        LOG.info("Model identified the element '{}' by majority vote. {} successful votes out of {}.",
                uiElement.name(), successfulIdentificationResults.size(), MODEL_VOTE_COUNT);
        var votesByColor = successfulIdentificationResults.stream()
                .collect(groupingBy(r -> r.boundingBoxId().toLowerCase(), counting()));
        var maxVotes = max(votesByColor.values());
        var winners = votesByColor.entrySet().stream()
                .filter(entry -> entry.getValue().equals(maxVotes))
                .map(Map.Entry::getKey)
                .map(CommonUtils::getColorByName)
                .toList();
        if (winners.size() > 1) {
            LOG.warn("Found multiple winners with {} votes for element '{}': {}. Selecting the one with the largest bounding box area.",
                    maxVotes, uiElement.name(), winners);
            return winners.stream()
                    .map(boxesWithColors::get)
                    .max(comparingDouble(box -> box.getWidth() * box.getHeight()))
                    .map(box -> new UiElementLocationResult(true, true, box, uiElement))
                    .orElseGet(()->new UiElementLocationResult(true, false, null, uiElement));
        } else {
            return new UiElementLocationResult(true, true, boxesWithColors.get(winners.getFirst()), uiElement);
        }
    }

    @NotNull
    private static List<UiElementIdentificationResult> getValidSuccessfulIdentificationResultsFromModelUsingQuorum(
            @NotNull UiElement uiElement,
            @NotNull BufferedImage resultingScreenshot,
            @NotNull Map<Color, Rectangle> boxesWithColors) {
        var validColors = boxesWithColors.keySet().stream().map(CommonUtils::getColorName).toList();
        var prompt = BestMatchingUiElementIdentificationPrompt.builder()
                .withUiElement(uiElement)
                .withScreenshot(resultingScreenshot)
                .withBoundingBoxColors(validColors)
                .build();
        try (var executor = newVirtualThreadPerTaskExecutor()) {
            List<Callable<UiElementIdentificationResult>> tasks = range(0, MODEL_VOTE_COUNT)
                    .mapToObj(i -> getIdentificationResultFromModel(i, prompt))
                    .toList();
            List<Future<UiElementIdentificationResult>> futures = executor.invokeAll(tasks);
            return futures.stream()
                    .map(future -> getFutureResult(future, "UI element identification by the model"))
                    .flatMap(Optional::stream)
                    .filter(r -> r.success() && validColors.contains(r.boundingBoxId()))
                    .toList();
        } catch (InterruptedException e) {
            currentThread().interrupt();
            LOG.error("Got interrupted while collecting UI element identification results by the model", e);
            return List.of();
        }
    }

    private static Callable<UiElementIdentificationResult> getIdentificationResultFromModel(int voteIndex,
                                                                                            BestMatchingUiElementIdentificationPrompt prompt) {
        return () -> {
            try (var model = getVisionModel(false)) {
                return model.generateAndGetResponseAsObject(prompt,
                        "identifying the best matching UI element (vote #%d)".formatted(voteIndex));
            }
        };
    }

    @NotNull
    private static PlottedUiElement getElementToPlot(UiElement element, List<Rectangle> matchedBoundingBoxes) {
        var colorsToUse = new LinkedList<>(availableBoundingBoxColors);
        return new PlottedUiElement(element.name(), element, matchedBoundingBoxes.stream()
                .collect(Collectors.toMap(_ -> colorsToUse.removeFirst(), identity())));
    }

    private static void markElementsToPlotWithBoundingBoxes(BufferedImage resultingScreenshot, PlottedUiElement elementToPlot,
                                                            String postfix) {
        Map<Color, Rectangle> elementBoundingBoxesByLabel = elementToPlot.boundingBoxes();
        drawBoundingBoxes(resultingScreenshot, elementBoundingBoxesByLabel);
        if (DEBUG_MODE) {
            saveScreenshot(resultingScreenshot, postfix);
        }
    }

    private record PlottedUiElement(String id, UiElement uiElement, Map<Color, Rectangle> boundingBoxes) {
    }

    private record UiElementLocationResult(boolean patternMatchFound, boolean visualMatchFound, Rectangle boundingBox,
                                           UiElement elementUsedForLocation) {
    }
}