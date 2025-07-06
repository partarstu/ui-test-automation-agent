/*
 * Copyright © 2025 Taras Paruta (partarstu@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may in a copy of the License at
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

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.opencv.calib3d.Calib3d;
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

    /**
     * A private record to hold intermediate match results including the rectangle and score.
     */
    private record MatchResultWithRectangle(Rectangle rectangle, double score) {
    }


    public static List<Rectangle> findMatchingRegionsWithORB(BufferedImage wholeScreenshot, BufferedImage elementScreenshot) {
        if (!initialized) {
            initialized = initializeOpenCv();
        }

        // --- Phase 0: Setup and Image Preparation ---
        // Convert BufferedImages to OpenCV Mat format
        Mat sceneMat = imdecode(new MatOfByte(imageToByteArray(wholeScreenshot, "png")), Imgcodecs.IMREAD_COLOR);
        Mat templateMat = imdecode(new MatOfByte(imageToByteArray(elementScreenshot, "png")), Imgcodecs.IMREAD_COLOR);

        // Convert images to grayscale for feature detection
        Mat sceneGray = new Mat();
        Mat templateGray = new Mat();
        Imgproc.cvtColor(sceneMat, sceneGray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(templateMat, templateGray, Imgproc.COLOR_BGR2GRAY);
        sceneMat.release();
        templateMat.release();

        // --- Phase 1: Feature Detection and Matching ---

        // Configure and create an ORB detector with parameters tuned for UI detection
        ORB orb = ORB.create(5000, 1.1f, 6, 31, 0, 2, ORB.HARRIS_SCORE, 31, 20);

        // Detect keypoints and compute descriptors for both template and scene
        MatOfKeyPoint templateKeyPoints = new MatOfKeyPoint();
        MatOfKeyPoint sceneKeyPoints = new MatOfKeyPoint();
        Mat templateDescriptors = new Mat();
        Mat sceneDescriptors = new Mat();
        orb.detectAndCompute(templateGray, new Mat(), templateKeyPoints, templateDescriptors);
        orb.detectAndCompute(sceneGray, new Mat(), sceneKeyPoints, sceneDescriptors);
        sceneGray.release();
        templateGray.release();

        // Check if enough features were found in the template
        if (templateKeyPoints.empty() || templateDescriptors.empty()) {
            LOG.warn("No ORB features found in the template image. Cannot perform matching.");
            templateKeyPoints.release();
            sceneKeyPoints.release();
            templateDescriptors.release();
            sceneDescriptors.release();
            return Collections.emptyList();
        }
        LOG.info("Got {} template keypoints and {} descriptors:", templateKeyPoints.toList().size(), templateDescriptors.rows());

        // Use Brute-Force Matcher with Hamming distance, appropriate for ORB
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        MatOfDMatch matches = new MatOfDMatch();
        matcher.match(templateDescriptors, sceneDescriptors, matches);

        // Initial filtering of matches based on distance
        List<DMatch> matchesList = matches.toList();
        matches.release();
        if (matchesList.isEmpty()) {
            LOG.info("No initial matches found between template and scene.");
            templateKeyPoints.release();
            sceneKeyPoints.release();
            templateDescriptors.release();
            sceneDescriptors.release();
            return Collections.emptyList();
        }
        LOG.info("Got {} matches", matchesList.size());
        double minDst = matchesList.stream().mapToDouble(m -> m.distance).min().orElse(Double.MAX_VALUE);
        List<DMatch> goodMatches = new ArrayList<>();
        for (DMatch match : matchesList) {
            if (match.distance <= Math.max(minDst * 3.0, 30.0)) { // Use a multiplier or a minimum threshold
                goodMatches.add(match);
            }
        }

        LOG.info("Got {} GOOD matches", goodMatches.size());
        if (goodMatches.size() < 6) { // Need at least 6 points for robust clustering/homography
            LOG.info("Not enough good matches ({}) found after initial filtering.", goodMatches.size());
            return Collections.emptyList();
        }

        // --- Phase 2: Spatial Clustering with DBSCAN ---

        // Prepare data for clustering by wrapping keypoints in a Clusterable interface
        List<KeyPoint> sceneKeyPointsList = sceneKeyPoints.toList();
        List<KeyPointClusterable> clusterInput = new ArrayList<>();
        for (DMatch match : goodMatches) {
            clusterInput.add(new KeyPointClusterable(match, sceneKeyPointsList.get(match.trainIdx)));
        }
        LOG.info("Got {} clusters", clusterInput.size());

        // Configure DBSCAN clusterer
        // eps: search radius, proportional to the template's size
        // minPts: minimum points to form a cluster, must be >= 4 for homography
        int templateDiagonal = (int) Math.hypot(elementScreenshot.getWidth(), elementScreenshot.getHeight());
        int eps = (int) (templateDiagonal * 0.3); // 30% of the diagonal as search radius
        int minPts = 4; // A safe number of points, more than the 4 required for homography

        DBSCANClusterer<KeyPointClusterable> dbscan = new DBSCANClusterer<>(eps, minPts);
        List<Cluster<KeyPointClusterable>> clusters = dbscan.cluster(clusterInput);

        if (clusters.isEmpty()) {
            LOG.info("DBSCAN did not find any dense clusters of keypoints.");
            return Collections.emptyList();
        }

        // --- Phase 3: Verification via Homography and Bounding Box Calculation ---

        List<Match> foundMatches = new ArrayList<>();
        List<KeyPoint> templateKeyPointsList = templateKeyPoints.toList();

        try {
            for (Cluster<KeyPointClusterable> cluster : clusters) {
                List<KeyPointClusterable> clusterPoints = cluster.getPoints();

                if (clusterPoints.size() < minPts) {
                    continue; // Skip clusters that are too small
                }

                // Extract source (template) and destination (scene) points for this cluster
                List<Point> srcPointsList = new ArrayList<>();
                List<Point> dstPointsList = new ArrayList<>();
                for (KeyPointClusterable kpClusterable : clusterPoints) {
                    srcPointsList.add(templateKeyPointsList.get(kpClusterable.getMatch().queryIdx).pt);
                    dstPointsList.add(kpClusterable.getKeyPoint().pt);
                }

                MatOfPoint2f srcPoints = new MatOfPoint2f();
                srcPoints.fromList(srcPointsList);
                MatOfPoint2f dstPoints = new MatOfPoint2f();
                dstPoints.fromList(dstPointsList);

                // Calculate homography using RANSAC to find the perspective transform
                Mat mask = new Mat();
                Mat homography = Calib3d.findHomography(srcPoints, dstPoints, Calib3d.RANSAC, 5.0, mask);
                srcPoints.release();
                dstPoints.release();

                if (homography.empty()) {
                    continue; // Could not compute a valid homography for this cluster
                }

                // Calculate the match score based on the inlier ratio from RANSAC
                int inliers = Core.countNonZero(mask);
                double score = (double) inliers / clusterPoints.size();

                // Define a threshold for a valid match score
                final double MIN_INLIER_RATIO = 0.75;
                if (score < MIN_INLIER_RATIO) {
                    continue;
                }

                // Transform the corners of the template to find the bounding box in the scene
                Mat templateCorners = new Mat(4, 1, CvType.CV_32FC2);
                Mat sceneCorners = new Mat();
                templateCorners.put(0, 0, 0, 0);
                templateCorners.put(1, 0, templateMat.cols(), 0);
                templateCorners.put(2, 0, templateMat.cols(), templateMat.rows());
                templateCorners.put(3, 0, 0, templateMat.rows());

                Core.perspectiveTransform(templateCorners, sceneCorners, homography);

                // Get the axis-aligned bounding box of the transformed corners
                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
                double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
                for (int i = 0; i < sceneCorners.rows(); i++) {
                    double[] point = sceneCorners.get(i, 0);
                    double x = point[0];
                    double y = point[1];
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }

                // Ensure coordinates are within the bounds of the screenshot
                minX = Math.max(0, minX);
                minY = Math.max(0, minY);
                maxX = Math.min(wholeScreenshot.getWidth(), maxX);
                maxY = Math.min(wholeScreenshot.getHeight(), maxY);

                Rectangle region = new Rectangle((int) minX, (int) minY, (int) (maxX - minX), (int) (maxY - minY));
                foundMatches.add(new Match(region, score));
                templateCorners.release();
                sceneCorners.release();
                mask.release();
                homography.release();
            }
        } catch (Exception e) {
            LOG.error("Got issue:", e);
        }

        // --- Finalization: Sort and Return Results ---

        // Sort the found matches by their score in descending order
        return foundMatches.stream()
                .sorted(comparingDouble(Match::score).reversed())
                .map(Match::region)
                .collect(toList());
    }


    private record MatchResult(java.awt.Point point, double score) {
    }

    private record Match(Rectangle region, double score) {
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