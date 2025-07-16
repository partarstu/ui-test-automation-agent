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

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.jetbrains.annotations.NotNull;
import org.opencv.core.*;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.lang.Math.max;
import static java.util.Comparator.comparingDouble;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.opencv.calib3d.Calib3d.RANSAC;
import static org.opencv.calib3d.Calib3d.findHomography;
import static org.opencv.core.Core.countNonZero;
import static org.opencv.core.Core.perspectiveTransform;
import static org.opencv.features2d.ORB.*;
import static org.opencv.imgcodecs.Imgcodecs.imdecode;
import static org.opencv.imgproc.Imgproc.*;
import static org.tarik.ta.utils.ImageUtils.imageToByteArray;


public class ImageMatchingUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ImageMatchingUtil.class);
    private static final double VISUAL_SIMILARITY_THRESHOLD = AgentConfig.getElementLocatorVisualSimilarityThreshold();
    private static final int TOP_VISUAL_MATCHES_TO_FIND = AgentConfig.getElementLocatorTopVisualMatches();
    private static final int MIN_GOOD_FEATURE_MATCHES = 10;
    private static final float LOWE_RATIO_THRESHOLD = 0.75f;
    private static final int KNN_MATCHES_PER_QUERY = 2;
    private static final int MINIMUM_CLUSTER_POPULATION = 5;
    private static final int MINIMUM_POINTS_FOR_HOMOGRAPHY = 6;

    // ORB parameters
    private static final int ORB_MAX_FEATURES = 150000;
    private static final float ORB_SCALE_FACTOR = 1.02f;
    private static final int ORB_N_LEVELS = 12;
    private static final int ORB_EDGE_THRESHOLD = 8;
    private static final int ORB_FIRST_LEVEL = 0;
    private static final int ORB_WTA_K = 2;
    private static final int ORB_PATCH_SIZE = 31;
    private static final int ORB_FAST_THRESHOLD = 6;
    private static final double FOUND_MATCHES_DIMENSION_DEVIATION_RATIO = AgentConfig.getFoundMatchesDimensionDeviationRatio();
    private static final double MAX_REPROJECTION_ERROR_THRESHOLD = 5.0;

    private static ORB ORB;
    private static boolean initialized = false;

    private static boolean initializeOpenCv() {
        Loader.load(opencv_java.class);
        ORB = create(ORB_MAX_FEATURES, ORB_SCALE_FACTOR, ORB_N_LEVELS, ORB_EDGE_THRESHOLD, ORB_FIRST_LEVEL, ORB_WTA_K, HARRIS_SCORE,
                ORB_PATCH_SIZE, ORB_FAST_THRESHOLD);
        return true;
    }

    public static List<Rectangle> findMatchingRegionsWithTemplateMatching(BufferedImage wholeScreenshot, BufferedImage elementScreenshot) {
        if (!initialized) {
            initialized = initializeOpenCv();
        }
        Mat source = imdecode(new MatOfByte(imageToByteArray(wholeScreenshot, "png")), Imgcodecs.IMREAD_COLOR);
        Mat template = imdecode(new MatOfByte(imageToByteArray(elementScreenshot, "png")), Imgcodecs.IMREAD_COLOR);
        Mat result = new Mat();
        matchTemplate(source, template, result, Imgproc.TM_CCOEFF_NORMED);
        List<MatchResult> matches = new ArrayList<>();
        while (matches.size() < TOP_VISUAL_MATCHES_TO_FIND) {
            var res = Core.minMaxLoc(result);
            if (res.maxVal >= VISUAL_SIMILARITY_THRESHOLD) {
                var maxLocation = res.maxLoc;
                matches.add(new MatchResult(new java.awt.Point((int) maxLocation.x, (int) maxLocation.y), res.maxVal));
                floodFill(result, new Mat(), maxLocation, new Scalar(0));
            } else {
                break;
            }
        }

        return matches.stream()
                .sorted(comparingDouble(MatchResult::score).reversed())
                .limit(TOP_VISUAL_MATCHES_TO_FIND)
                .map(match ->
                        new Rectangle(match.point(), new Dimension(elementScreenshot.getWidth(), elementScreenshot.getHeight())))
                .toList();
    }

    public static List<Rectangle> findMatchingRegionsWithORB(BufferedImage wholeScreenshot, BufferedImage elementScreenshot) {
        return findMatchingRegionsWithORB(wholeScreenshot, elementScreenshot, FOUND_MATCHES_DIMENSION_DEVIATION_RATIO);
    }

    public static List<Rectangle> findMatchingRegionsWithORB(BufferedImage wholeScreenshot, BufferedImage elementScreenshot,
                                                             double deviationRatio) {
        if (!initialized) {
            initialized = initializeOpenCv();
        }

        Mat wholeMat = imdecode(new MatOfByte(imageToByteArray(wholeScreenshot, "png")), Imgcodecs.IMREAD_GRAYSCALE);
        Mat elementMat = imdecode(new MatOfByte(imageToByteArray(elementScreenshot, "png")), Imgcodecs.IMREAD_GRAYSCALE);
        if (elementMat.empty() || wholeMat.empty()) {
            LOG.error("Cannot read images, one or both are empty.");
            return Collections.emptyList();
        }

        MatOfKeyPoint keypointsElement = new MatOfKeyPoint();
        Mat descriptorsElement = new Mat();
        ORB.detectAndCompute(elementMat, new Mat(), keypointsElement, descriptorsElement);
        if (descriptorsElement.empty()) {
            LOG.warn("No descriptors found in element screenshot, please verify the config of ORB algorithm.");
            return List.of();
        }

        MatOfKeyPoint keypointsWhole = new MatOfKeyPoint();
        Mat descriptorsWhole = new Mat();
        ORB.detectAndCompute(wholeMat, new Mat(), keypointsWhole, descriptorsWhole);
        if (descriptorsWhole.empty()) {
            LOG.warn("No descriptors found in the whole screenshot, either the image or the config of ORB algorithm is invalid.");
            return List.of();
        }

        List<DMatch> goodMatchesList = getGoodMatches(descriptorsElement, descriptorsWhole);
        if (goodMatchesList.size() < MIN_GOOD_FEATURE_MATCHES) {
            LOG.info("Not enough good matches found ({}) to form reliable clusters.", goodMatchesList.size());
            return List.of();
        }

        List<MatchResultWithRectangle> foundMatches = new ArrayList<>();
        List<KeyPoint> elementKeyPointsList = keypointsElement.toList();
        double maxAllowedRegionWidth = elementScreenshot.getWidth() * (1 + deviationRatio);
        double maxAllowedRegionHeight = elementScreenshot.getHeight() * (1 + deviationRatio);
        getClusters(elementScreenshot, keypointsWhole, goodMatchesList).forEach(cluster ->
                getIdentifiedRegionBoundingBox(cluster, elementKeyPointsList, elementMat, maxAllowedRegionWidth, maxAllowedRegionHeight)
                        .ifPresent(foundMatches::add));

        return foundMatches.stream()
                .sorted(comparingDouble(MatchResultWithRectangle::score).reversed())
                .limit(TOP_VISUAL_MATCHES_TO_FIND)
                .map(MatchResultWithRectangle::rectangle)
                .collect(toList());
    }

    private static List<Cluster<KeyPointClusterable>> getClusters(BufferedImage elementScreenshot, MatOfKeyPoint keypointsWhole,
                                                                  List<DMatch> goodMatchesList) {
        List<KeyPoint> wholeKeyPointsList = keypointsWhole.toList();
        List<KeyPointClusterable> clusterInput = new ArrayList<>();
        for (DMatch goodMatch : goodMatchesList) {
            clusterInput.add(new KeyPointClusterable(goodMatch, wholeKeyPointsList.get(goodMatch.trainIdx)));
        }
        double eps = max(elementScreenshot.getWidth(), elementScreenshot.getHeight());
        DBSCANClusterer<KeyPointClusterable> dbscan = new DBSCANClusterer<>(eps, MINIMUM_CLUSTER_POPULATION);
        return dbscan.cluster(clusterInput);
    }

    @NotNull
    private static List<DMatch> getGoodMatches(Mat descriptorsElement, Mat descriptorsWhole) {
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        matcher.knnMatch(descriptorsElement, descriptorsWhole, knnMatches, KNN_MATCHES_PER_QUERY);
        List<DMatch> goodMatchesList = new ArrayList<>();
        for (MatOfDMatch knnMatch : knnMatches) {
            DMatch[] matches = knnMatch.toArray();
            if (matches.length > 1 && matches[0].distance < LOWE_RATIO_THRESHOLD * matches[1].distance) {
                goodMatchesList.add(matches[0]);
            }
        }
        return goodMatchesList;
    }

    private static Optional<MatchResultWithRectangle> getIdentifiedRegionBoundingBox(Cluster<KeyPointClusterable> cluster,
                                                                                     List<KeyPoint> elementKeyPointsList, Mat elementMat,
                                                                                     double maxAllowedRegionWidth,
                                                                                     double maxAllowedRegionHeight) {
        List<Point> elementPoints = new ArrayList<>();
        List<Point> wholePoints = new ArrayList<>();

        for (KeyPointClusterable kpc : cluster.getPoints()) {
            elementPoints.add(elementKeyPointsList.get(kpc.getMatch().queryIdx).pt);
            wholePoints.add(kpc.getKeyPoint().pt);
        }

        // Need at least MINIMUM_POINTS_FOR_HOMOGRAPHY points to calculate homography
        if (elementPoints.size() < MINIMUM_POINTS_FOR_HOMOGRAPHY) {
            LOG.info("Not enough points ({}) to calculate homography for a cluster. Minimum required: {}",
                    elementPoints.size(), MINIMUM_POINTS_FOR_HOMOGRAPHY);
            return empty();
        }

        // Find the perspective transformation between the element and the cluster in the whole image
        MatOfPoint2f elementPtsMat = new MatOfPoint2f(elementPoints.toArray(new Point[0]));
        MatOfPoint2f wholePtsMat = new MatOfPoint2f(wholePoints.toArray(new Point[0]));
        Mat mask = new Mat();
        Mat homography = findHomography(elementPtsMat, wholePtsMat, RANSAC, MAX_REPROJECTION_ERROR_THRESHOLD, mask);
        if (homography.empty() || homography.rows() != 3 || homography.cols() != 3) {
            LOG.info("Homography matrix is empty or has invalid dimensions. Cannot proceed with homography transformation.");
            return empty();
        }

        // Use the homography to project the corners of the element onto the whole screenshot
        Mat elementCorners = new Mat(4, 1, CvType.CV_32FC2);
        elementCorners.put(0, 0, 0, 0);
        elementCorners.put(1, 0, elementMat.cols(), 0);
        elementCorners.put(2, 0, elementMat.cols(), elementMat.rows());
        elementCorners.put(3, 0, 0, elementMat.rows());
        Mat sceneCorners = new Mat();
        perspectiveTransform(elementCorners, sceneCorners, homography);

        // Create a bounding rectangle from the transformed corners
        Rect boundingRect = boundingRect(new MatOfPoint(
                new Point(sceneCorners.get(0, 0)),
                new Point(sceneCorners.get(1, 0)),
                new Point(sceneCorners.get(2, 0)),
                new Point(sceneCorners.get(3, 0))
        ));

        if (boundingRect.width <= maxAllowedRegionWidth && boundingRect.height <= maxAllowedRegionHeight) {
            // The mainScore is the number of inliers from the RANSAC homography calculation, which is a robust measure of match quality.
            Rectangle boundingBox = new Rectangle(boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height);
            return of(new MatchResultWithRectangle(boundingBox, countNonZero(mask)));
        } else {
            return empty();
        }
    }

    private record MatchResultWithRectangle(Rectangle rectangle, double score) {
    }

    private record MatchResult(java.awt.Point point, double score) {
    }

    private static class KeyPointClusterable implements Clusterable {
        private final double[] point;
        private final DMatch match;
        private final KeyPoint keyPoint;

        public KeyPointClusterable(DMatch match, KeyPoint keyPoint) {
            this.match = match;
            this.keyPoint = keyPoint;
            this.point = new double[]{keyPoint.pt.x, keyPoint.pt.y};
        }

        public DMatch getMatch() {
            return match;
        }

        public KeyPoint getKeyPoint() {
            return keyPoint;
        }

        @Override
        public double[] getPoint() {
            return point;
        }
    }
}