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
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tarik.ta.AgentConfig;

import java.awt.*;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;
import static org.opencv.imgcodecs.Imgcodecs.imdecode;
import static org.opencv.imgproc.Imgproc.floodFill;
import static org.opencv.imgproc.Imgproc.matchTemplate;
import static org.tarik.ta.utils.ImageUtils.imageToByteArray;

public class ImageMatchingUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ImageMatchingUtil.class);
    private static final double VISUAL_SIMILARITY_THRESHOLD = AgentConfig.getElementLocatorVisualSimilarityThreshold();
    private static final int TOP_VISUAL_MATCHES_TO_FIND = AgentConfig.getElementLocatorTopVisualMatches();
    private static boolean initialized = false;

    private static boolean initializeOpenCv() {
        Loader.load(opencv_java.class);
        return true;
    }

    public static List<Rectangle> findMatchingRegions(BufferedImage wholeScreenshot, BufferedImage elementScreenshot) {
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
                matches.add(new MatchResult(new Point((int) maxLocation.x, (int) maxLocation.y), res.maxVal));
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

    /**
     * Finds matching regions using the ORB (Oriented FAST and Rotated BRIEF) feature detector.
     * This method is generally more robust to changes in scale, rotation, and lighting than template matching.
     *
     * @param wholeScreenshot   The larger image to search within.
     * @param elementScreenshot The smaller template image to search for.
     * @return A list containing a single Rectangle of the best match, or an empty list if no suitable match is found.
     */
    public static List<Rectangle> findMatchingRegionsWithORB(BufferedImage wholeScreenshot, BufferedImage elementScreenshot) {
        if (!initialized) {
            initialized = initializeOpenCv();
        }
        // ======================= TUNING PARAMETERS =======================
        // 1. Increase this to allow more matches to be considered "good".
        float ratioThresh = 1.2f;
        // 2. Increase this to group points that are further apart into the same cluster.
        double epsMultiplier = 4;
        // 3. Decrease this to allow smaller clusters to be processed.
        int minMatchesPerCluster = 8;
        // 4. Increase this to be more tolerant of imprecise matches during homography calculation.
        double ransacReprojThreshold = 10.0;
        // =================================================================

        Mat sceneMat = imdecode(new MatOfByte(imageToByteArray(wholeScreenshot, "png")), Imgcodecs.IMREAD_GRAYSCALE);
        Mat templateMat = imdecode(new MatOfByte(imageToByteArray(elementScreenshot, "png")), Imgcodecs.IMREAD_GRAYSCALE);

        if (sceneMat.empty() || templateMat.empty()) {
            LOG.error("Could not load images for ORB matching.");
            return Collections.emptyList();
        }

        ORB orb = ORB.create();
        MatOfKeyPoint keypointsScene = new MatOfKeyPoint();
        MatOfKeyPoint keypointsTemplate = new MatOfKeyPoint();
        Mat descriptorsScene = new Mat();
        Mat descriptorsTemplate = new Mat();

        orb.detectAndCompute(sceneMat, new Mat(), keypointsScene, descriptorsScene);
        orb.detectAndCompute(templateMat, new Mat(), keypointsTemplate, descriptorsTemplate);

        if (descriptorsTemplate.empty() || descriptorsScene.empty()) {
            LOG.warn("No descriptors could be computed for one or both images.");
            return Collections.emptyList();
        }

        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        matcher.knnMatch(descriptorsTemplate, descriptorsScene, knnMatches, 2);

        List<DMatch> goodMatchesList = new ArrayList<>();
        for (MatOfDMatch knnMatch : knnMatches) {
            if (knnMatch.rows() > 1) {
                DMatch[] matches = knnMatch.toArray();
                if (matches[0].distance < ratioThresh * matches[1].distance) {
                    goodMatchesList.add(matches[0]);
                }
            }
        }

        // --- DEBUG LOG 1 ---
        LOG.info("ORB: Found {} total 'good' matches after ratio test.", goodMatchesList.size());

        if (goodMatchesList.isEmpty()) {
            return Collections.emptyList();
        }

        double eps = Math.sqrt(Math.pow(templateMat.cols(), 2) + Math.pow(templateMat.rows(), 2)) * epsMultiplier;
        var clusteredMatches = clusterMatches(goodMatchesList, keypointsScene.toList(), eps);

        // --- DEBUG LOG 2 ---
        LOG.info("ORB: Grouped matches into {} clusters. Cluster sizes: {}",
                clusteredMatches.size(),
                clusteredMatches.stream().map(List::size).collect(toList()));

        List<Rectangle> foundRectangles = new ArrayList<>();
        KeyPoint[] keypointsTemplateArray = keypointsTemplate.toArray();
        KeyPoint[] keypointsSceneArray = keypointsScene.toArray();

        try{
            for (int i = 0; i < clusteredMatches.size(); i++) {
                List<DMatch> cluster = clusteredMatches.get(i);
                if (cluster.size() < minMatchesPerCluster) {
                    // --- DEBUG LOG 3 (Optional) ---
                    // LOG.info("ORB: Skipping cluster {} because its size ({}) is less than min ({}).", i, cluster.size(), minMatchesPerCluster);
                    continue;
                }

                var templatePointsForCluster =
                        cluster.stream().map(match -> keypointsTemplateArray[match.queryIdx].pt).collect(toList());
                var scenePointsForCluster = cluster.stream().map(match -> keypointsSceneArray[match.trainIdx].pt).collect(toList());

                MatOfPoint2f templateMatOfPoint2f = new MatOfPoint2f(templatePointsForCluster.toArray(new org.opencv.core.Point[0]));
                MatOfPoint2f sceneMatOfPoint2f = new MatOfPoint2f(scenePointsForCluster.toArray(new org.opencv.core.Point[0]));

                Mat homography = Calib3d.findHomography(templateMatOfPoint2f, sceneMatOfPoint2f, Calib3d.RANSAC, ransacReprojThreshold);

                if (homography.empty() || homography.cols() != 3 || homography.rows() != 3) {
                    // --- DEBUG LOG 4 ---
                    LOG.warn("ORB: Could not find a valid homography for cluster {} with {} matches.", i, cluster.size());
                    continue;
                }

                Mat templateCorners = new Mat(4, 1, CvType.CV_32FC2);
                templateCorners.put(0, 0, new double[]{0, 0});
                templateCorners.put(1, 0, new double[]{templateMat.cols(), 0});
                templateCorners.put(2, 0, new double[]{templateMat.cols(), templateMat.rows()});
                templateCorners.put(3, 0, new double[]{0, templateMat.rows()});

                Mat sceneCorners = new Mat();
                Core.perspectiveTransform(templateCorners, sceneCorners, homography);

                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
                float maxX = 0, maxY = 0;
                float[] sceneCornersData = new float[8];
                sceneCorners.get(0, 0, sceneCornersData);

                for (int j = 0; j < 4; j++) {
                    float x = sceneCornersData[j * 2];
                    float y = sceneCornersData[j * 2 + 1];
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }

                if (maxX > minX && maxY > minY) {
                    minX = Math.max(0, minX);
                    minY = Math.max(0, minY);
                    maxX = Math.min(sceneMat.cols(), maxX);
                    maxY = Math.min(sceneMat.rows(), maxY);
                    foundRectangles.add(new Rectangle((int) minX, (int) minY, (int) (maxX - minX), (int) (maxY - minY)));
                }
            }
        } catch (Exception e) {
            LOG.error("Couldn't  work", e);
            return List.of();
        }

        LOG.info("ORB: Found {} final object(s) after processing all clusters:", foundRectangles);
        return foundRectangles;
    }

    /**
     * Clusters DMatch objects based on the spatial proximity of their corresponding scene keypoints.
     *
     * @param matches       The list of matches to cluster.
     * @param sceneKeypoints The list of all keypoints from the scene.
     * @param eps           The maximum distance between two points to be considered in the same cluster.
     * @return A list of lists, where each inner list represents a cluster of matches.
     */
    private static List<List<DMatch>> clusterMatches(List<DMatch> matches, List<KeyPoint> sceneKeypoints, double eps) {
        List<List<DMatch>> clusters = new ArrayList<>();
        boolean[] visited = new boolean[matches.size()];

        for (int i = 0; i < matches.size(); i++) {
            if (visited[i]) {
                continue;
            }
            visited[i] = true;

            List<DMatch> newCluster = new ArrayList<>();
            LinkedList<DMatch> queue = new LinkedList<>();
            queue.add(matches.get(i));
            newCluster.add(matches.get(i));

            while (!queue.isEmpty()) {
                DMatch currentMatch = queue.poll();
                var currentPoint = sceneKeypoints.get(currentMatch.trainIdx).pt;

                for (int j = 0; j < matches.size(); j++) {
                    if (visited[j]) {
                        continue;
                    }

                    DMatch otherMatch = matches.get(j);
                    var otherPoint = sceneKeypoints.get(otherMatch.trainIdx).pt;

                    double distance = Core.norm(new MatOfPoint2f(currentPoint), new MatOfPoint2f(otherPoint));
                    if (distance < eps) {
                        visited[j] = true;
                        queue.add(otherMatch);
                        newCluster.add(otherMatch);
                    }
                }
            }
            clusters.add(newCluster);
        }
        return clusters;
    }

    private record MatchResult(Point point, double score) {
    }
}
