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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.tarik.ta.utils.BoundingBoxUtil.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BoundingBoxUtil Tests")
class BoundingBoxUtilTest {

    @Mock
    private BufferedImage mockImage;
    @Mock
    private Graphics2D mockGraphics;
    @Mock
    private Path mockBasePath;
    @Mock
    private Path mockFinalPath;
    @Mock
    private File mockFile;

    private MockedStatic<ImageIO> imageIoMockedStatic;
    private MockedStatic<Paths> pathsMockedStatic;
    private MockedStatic<LocalDateTime> localDateTimeMockedStatic;
    private MockedStatic<OpenCvInitializer> openCvInitializerMockedStatic;

    private static final String SCREENSHOTS_SAVE_FOLDER = "screens";
    private static final Color TEST_COLOR = Color.RED;
    private static final Rectangle TEST_RECTANGLE = new Rectangle(10, 10, 100, 100);
    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2025, 4, 17, 15, 0, 0);

    @BeforeEach
    void setUp() {
        // Mock static methods
        imageIoMockedStatic = mockStatic(ImageIO.class);
        pathsMockedStatic = mockStatic(Paths.class);
        localDateTimeMockedStatic = mockStatic(LocalDateTime.class);
        openCvInitializerMockedStatic = mockStatic(OpenCvInitializer.class);

        lenient().when(mockImage.createGraphics()).thenReturn(mockGraphics);
        lenient().when(mockGraphics.getFontMetrics()).thenReturn(mock(FontMetrics.class));
        openCvInitializerMockedStatic.when(OpenCvInitializer::initialize).thenAnswer(_ -> null);
    }

    @AfterEach
    void tearDown() {
        imageIoMockedStatic.close();
        pathsMockedStatic.close();
        localDateTimeMockedStatic.close();
        openCvInitializerMockedStatic.close();
    }

    @Test
    @DisplayName("mergeOverlappingRectangles: Should return empty list when input is empty")
    void mergeOverlappingRectanglesWhenInputIsEmptyThenReturnEmptyList() {
        // Given
        Collection<Rectangle> rectangles = new ArrayList<>();

        // When
        List<Rectangle> result = mergeOverlappingRectangles(rectangles);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("mergeOverlappingRectangles: Should return the same list when no rectangles overlap")
    void mergeOverlappingRectanglesWhenNoOverlapThenReturnSameList() {
        // Given
        Rectangle r1 = new Rectangle(0, 0, 10, 10);
        Rectangle r2 = new Rectangle(20, 20, 10, 10);
        Collection<Rectangle> rectangles = List.of(r1, r2);

        // When
        List<Rectangle> result = mergeOverlappingRectangles(rectangles);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains(r1));
        assertTrue(result.contains(r2));
    }

    @Test
    @DisplayName("mergeOverlappingRectangles: Should merge two overlapping rectangles")
    void mergeOverlappingRectanglesWhenTwoOverlapThenMergeThem() {
        // Given
        Rectangle r1 = new Rectangle(0, 0, 10, 10);
        Rectangle r2 = new Rectangle(5, 5, 10, 10);
        Collection<Rectangle> rectangles = List.of(r1, r2);
        Rectangle expectedMerge = new Rectangle(0, 0, 15, 15); // r1.union(r2)

        // When
        List<Rectangle> result = mergeOverlappingRectangles(rectangles);

        // Then
        assertEquals(1, result.size());
        assertEquals(expectedMerge, result.getFirst());
        openCvInitializerMockedStatic.verify(OpenCvInitializer::initialize, atLeast(1));
    }

    @Test
    @DisplayName("mergeOverlappingRectangles: Should merge multiple overlapping rectangles into one")
    void mergeOverlappingRectanglesWhenMultipleOverlapThenMergeToOne() {
        // Given
        Rectangle r1 = new Rectangle(0, 0, 10, 10);
        Rectangle r2 = new Rectangle(5, 5, 10, 10);
        Rectangle r3 = new Rectangle(8, 8, 10, 10);
        Collection<Rectangle> rectangles = List.of(r1, r2, r3);
        Rectangle expectedMerge = new Rectangle(0, 0, 18, 18); // (r1 union r2) union r3

        // When
        List<Rectangle> result = mergeOverlappingRectangles(rectangles);

        // Then
        assertEquals(1, result.size());
        assertEquals(expectedMerge, result.getFirst());
        openCvInitializerMockedStatic.verify(OpenCvInitializer::initialize, atLeast(1));
    }

    @Test
    @DisplayName("mergeOverlappingRectangles: Should handle rectangles touching at edges (not overlapping)")
    void mergeOverlappingRectanglesWhenRectanglesTouchThenDoNotMerge() {
        // Given
        Rectangle r1 = new Rectangle(0, 0, 10, 10);
        Rectangle r2 = new Rectangle(10, 0, 10, 10); // Touches r1 on the right edge
        Collection<Rectangle> rectangles = List.of(r1, r2);

        // When
        List<Rectangle> result = mergeOverlappingRectangles(rectangles);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains(r1));
        assertTrue(result.contains(r2));
        openCvInitializerMockedStatic.verify(OpenCvInitializer::initialize, times(1));
    }

    @Test
    @DisplayName("mergeOverlappingRectangles: Should merge complex overlaps into multiple distinct rectangles")
    void mergeOverlappingRectanglesWhenComplexOverlapThenMergeIntoMultiple() {
        // Given
        Rectangle r1 = new Rectangle(0, 0, 10, 10);
        Rectangle r2 = new Rectangle(5, 5, 10, 10); // Overlaps r1
        Rectangle r3 = new Rectangle(30, 30, 10, 10);
        Rectangle r4 = new Rectangle(35, 35, 10, 10); // Overlaps r3
        Rectangle r5 = new Rectangle(60, 60, 10, 10); // Non-overlapping
        var rectangles = List.of(r1, r2, r3, r4, r5);
        Rectangle expectedMerge12 = new Rectangle(0, 0, 15, 15);
        Rectangle expectedMerge34 = new Rectangle(30, 30, 15, 15);

        // When
        List<Rectangle> result = mergeOverlappingRectangles(rectangles);

        // Then
        assertEquals(3, result.size());
        assertTrue(result.contains(expectedMerge12));
        assertTrue(result.contains(expectedMerge34));
        assertTrue(result.contains(r5));
    }

    @Test
    @DisplayName("mergeOverlappingRectangles: Should merge fully contained rectangles")
    void mergeOverlappingRectanglesWhenOneContainsAnotherThenMerge() {
        // Given
        Rectangle r1 = new Rectangle(0, 0, 20, 20);
        Rectangle r2 = new Rectangle(5, 5, 10, 10); // Fully contained within r1
        Collection<Rectangle> rectangles = List.of(r1, r2);

        // When
        List<Rectangle> result = mergeOverlappingRectangles(rectangles);

        // Then
        assertEquals(1, result.size());
        assertEquals(r1, result.getFirst()); // The union should be the larger rectangle
    }

    @Test
    @DisplayName("drawBoundingBox: Should draw rectangle without saving")
    void drawBoundingBoxWhenSaveIsFalseThenDrawOnly() {
        // When
        BoundingBoxInfo info = drawBoundingBox(mockImage, TEST_RECTANGLE, TEST_COLOR);

        // Then
        verify(mockImage).createGraphics();
        verify(mockGraphics).setColor(TEST_COLOR);
        verify(mockGraphics).setStroke(any(BasicStroke.class));
        verify(mockGraphics).drawRect(TEST_RECTANGLE.x, TEST_RECTANGLE.y, TEST_RECTANGLE.width, TEST_RECTANGLE.height);
        verify(mockGraphics).dispose();
        imageIoMockedStatic.verify(() -> ImageIO.write(any(), anyString(), any(File.class)), never());
        openCvInitializerMockedStatic.verify(OpenCvInitializer::initialize, times(1));
        assertNotNull(info);
    }

    @Test
    @DisplayName("drawBoundingBox overload: Should call main drawBoundingBox with defaults")
    void drawBoundingBoxSimpleOverload() {
        // When
        BoundingBoxInfo info = drawBoundingBox(mockImage, TEST_RECTANGLE, TEST_COLOR);

        // Then
        // Verify the main drawBoundingBox logic is invoked with expected defaults
        verify(mockImage).createGraphics();
        verify(mockGraphics).setColor(TEST_COLOR);
        verify(mockGraphics).drawRect(TEST_RECTANGLE.x, TEST_RECTANGLE.y, TEST_RECTANGLE.width, TEST_RECTANGLE.height);
        verify(mockGraphics).dispose();
        imageIoMockedStatic.verify(() -> ImageIO.write(any(), anyString(), any(File.class)), never());
        openCvInitializerMockedStatic.verify(OpenCvInitializer::initialize, times(1));
        assertNotNull(info);
    }

    @Test
    @DisplayName("drawBoundingBoxes map overload: Should call drawBoundingBox for each entry")
    void drawBoundingBoxesMapOverload() {
        // Given
        Color color1 = Color.BLUE;
        Rectangle rect1 = new Rectangle(20, 20, 50, 50);
        Color color2 = Color.GREEN;
        Rectangle rect2 = new Rectangle(80, 80, 60, 60);
        Map<Color, Rectangle> rectanglesMap = Map.of(color1, rect1, color2, rect2);

        // When
        drawBoundingBoxes(mockImage, rectanglesMap);

        // Then
        verify(mockImage, atLeastOnce()).createGraphics();
        verify(mockGraphics).setColor(eq(color1));
        verify(mockGraphics).drawRect(eq(rect1.x), eq(rect1.y), eq(rect1.width), eq(rect1.height));
        verify(mockGraphics).setColor(eq(color2));
        verify(mockGraphics).drawRect(eq(rect2.x), eq(rect2.y), eq(rect2.width), eq(rect2.height));
        verify(mockGraphics, atLeastOnce()).dispose();
        imageIoMockedStatic.verify(() -> ImageIO.write(any(), anyString(), any(File.class)), never());
        openCvInitializerMockedStatic.verify(OpenCvInitializer::initialize, atLeast(1));
    }
}
