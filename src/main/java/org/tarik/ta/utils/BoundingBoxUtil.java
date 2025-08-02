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

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static java.util.stream.Stream.concat;
import static org.tarik.ta.utils.BoundingBoxUtil.OpenCvInitializer.initialize;


public class BoundingBoxUtil {
    public static void drawBoundingBoxes(BufferedImage image, Map<Color, Rectangle> rectangleByLabel) {
        initialize();
        rectangleByLabel.forEach((boxColor, box) -> drawBoundingBox(image, box, boxColor));
    }

    public static BoundingBoxInfo drawBoundingBox(BufferedImage image, Rectangle rectangle, Color boxColor) {
        initialize();
        Graphics2D g2d = image.createGraphics();
        try {
            int boundingBoxLineStroke = 4;
            g2d.setColor(boxColor);
            g2d.setStroke(new BasicStroke(boundingBoxLineStroke));
            g2d.drawRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
            return new BoundingBoxInfo(boxColor);
        } finally {
            g2d.dispose();
        }
    }

    public static List<Rectangle> mergeOverlappingRectangles(Collection<Rectangle> rectangles) {
        initialize();
        List<Rectangle> results = new LinkedList<>();
        List<Rectangle> uniqueRectangles = new LinkedList<>();
        List<Rectangle> mergedRectangles = new LinkedList<>();
        rectangles.stream()
                .filter(me -> !mergedRectangles.contains(me))
                .forEach(me -> {
                    rectangles.stream()
                            .filter(other -> other != me && !mergedRectangles.contains(other))
                            .forEach(other -> {
                                if (other.intersects(me)) {
                                    results.add(me.union(other));
                                    mergedRectangles.add(other);
                                    mergedRectangles.add(me);
                                }
                            });
                    if (!mergedRectangles.contains(me)) {
                        uniqueRectangles.add(me);
                    }
                });

        var finalResults = results;
        if (!finalResults.isEmpty()) {
            finalResults = mergeOverlappingRectangles(concat(results.stream(), uniqueRectangles.stream()).toList());
        }

        return concat(finalResults.stream(), uniqueRectangles.stream()).distinct().toList();
    }

    public static double calculateIoU(Rectangle r1, Rectangle r2) {
        initialize();
        Rectangle intersection = r1.intersection(r2);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        double intersectionArea = intersection.getWidth() * intersection.getHeight();
        double unionArea = (r1.getWidth() * r1.getHeight()) + (r2.getWidth() * r2.getHeight()) - intersectionArea;
        return unionArea == 0 ? 0 : intersectionArea / unionArea;
    }

    public record BoundingBoxInfo(Color boxColor) {
    }

    static class OpenCvInitializer {
        private static boolean initialized;

        static void initialize() {
            if (!initialized) {
                Loader.load(opencv_java.class);
                initialized = true;
            }
        }
    }
}