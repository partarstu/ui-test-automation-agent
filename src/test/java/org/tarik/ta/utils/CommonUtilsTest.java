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

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.tarik.ta.utils.CommonUtils.*;

@SuppressWarnings("ALL")
@ExtendWith(MockitoExtension.class)
@DisplayName("CommonUtils Tests")
class CommonUtilsTest {
    @Mock
    private Toolkit mockToolkit;
    @Mock
    private GraphicsEnvironment mockGraphicsEnvironment;
    @Mock
    private GraphicsDevice mockGraphicsDevice;
    @Mock
    private GraphicsConfiguration mockGraphicsConfiguration;
    @Mock
    private AffineTransform mockAffineTransform;
    @Mock
    private Dimension mockScreenSize;
    @Mock
    private PointerInfo mockPointerInfo;
    @Mock
    private MultiResolutionImage mockMultiResolutionImage;
    @Mock
    private BufferedImage mockBufferedImage;
    @Mock
    private FileReader mockFileReader;
    @Mock
    private Field mockColorField;
    @Mock
    private JFrame mockFrame;
    @Mock
    private JPanel mockPanel;


    // Static mocks
    private MockedStatic<Toolkit> toolkitMockedStatic;
    private MockedStatic<GraphicsEnvironment> graphicsEnvironmentMockedStatic;
    private MockedStatic<Robot> robotMockedStatic;
    private MockedStatic<MouseInfo> mouseInfoMockedStatic;
    private MockedStatic<ImageUtils> imageUtilsMockedStatic;
    private MockedStatic<Color> colorMockedStatic; // For mocking Color.class.getField

    // Constants
    private static final String TEST_FILE_PATH = "test.json";
    private static final String VALID_JSON = "{\"key\":\"value\"}";
    private static final String INVALID_JSON = "{key:value}"; // Missing quotes
    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;
    private static final double SCALE_FACTOR = 1.5;

    // Helper record for JSON deserialization test
    private record TestData(String key) {
    }

    @BeforeEach
    void setUp() {
        toolkitMockedStatic = mockStatic(Toolkit.class);
        graphicsEnvironmentMockedStatic = mockStatic(GraphicsEnvironment.class);
        robotMockedStatic = mockStatic(Robot.class);
        mouseInfoMockedStatic = mockStatic(MouseInfo.class);
        imageUtilsMockedStatic = mockStatic(ImageUtils.class);
        colorMockedStatic = mockStatic(Color.class);

        // Toolkit
        lenient().when(Toolkit.getDefaultToolkit()).thenReturn(mockToolkit);
        lenient().when(mockToolkit.getScreenSize()).thenReturn(mockScreenSize);
        lenient().when(mockScreenSize.getWidth()).thenReturn((double) SCREEN_WIDTH);
        lenient().when(mockScreenSize.getHeight()).thenReturn((double) SCREEN_HEIGHT);

        // Graphics Environment (Default setup for non-scaled)
        lenient().when(GraphicsEnvironment.getLocalGraphicsEnvironment()).thenReturn(mockGraphicsEnvironment);
        lenient().when(mockGraphicsEnvironment.getDefaultScreenDevice()).thenReturn(mockGraphicsDevice);
        lenient().when(mockGraphicsDevice.getDefaultConfiguration()).thenReturn(mockGraphicsConfiguration);
        lenient().when(mockGraphicsConfiguration.getDefaultTransform()).thenReturn(mockAffineTransform);
        lenient().when(mockAffineTransform.getScaleX()).thenReturn(1.0); // Default: no scaling
        lenient().when(mockAffineTransform.getScaleY()).thenReturn(1.0); // Default: no scaling

        lenient().when(mockMultiResolutionImage.getResolutionVariants()).thenReturn(List.of(mockBufferedImage)); // Simple case
        lenient().when(ImageUtils.toBufferedImage(eq(mockBufferedImage), anyInt(), anyInt())).thenReturn(mockBufferedImage);

        // MouseInfo
        lenient().when(MouseInfo.getPointerInfo()).thenReturn(mockPointerInfo);
        lenient().when(mockPointerInfo.getLocation()).thenReturn(new Point(100, 150));
    }

    @AfterEach
    void tearDown() {
        toolkitMockedStatic.close();
        graphicsEnvironmentMockedStatic.close();
        robotMockedStatic.close();
        mouseInfoMockedStatic.close();
        imageUtilsMockedStatic.close();
        colorMockedStatic.close();
    }

    @Test
    @DisplayName("getStaticFieldValue: Should return static field value")
    void getStaticFieldValueSuccess() throws NoSuchFieldException, IllegalAccessException {
        // Given
        Field colorField = Color.class.getField("RED"); // Use a real static field

        // When
        Object value = getStaticFieldValue(colorField);

        // Then
        assertEquals(Color.RED, value);
    }

    @Test
    @DisplayName("getColorByName: Should return correct Color for valid name")
    void getColorByNameValid() {
        // Given
        String colorName = "BLUE";

        // When
        Color result = getColorByName(colorName);

        // Then
        assertEquals(Color.BLUE, result);
    }

    @Test
    @DisplayName("getColorByName: Should return correct Color for valid lowercase name")
    void getColorByNameValidLowercase() {
        // Given
        String colorName = "green";

        // When
        Color result = getColorByName(colorName);

        // Then
        assertEquals(Color.GREEN, result);
    }

    @Test
    @DisplayName("getColorByName: Should throw exception for invalid name")
    void getColorByNameInvalid() {
        // Given
        String invalidColorName = "NonExistentColor";

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            getColorByName(invalidColorName);
        });
        assertTrue(exception.getMessage().contains(invalidColorName));
    }


    @Test
    @DisplayName("getColorName: Should return correct name for standard Color")
    void getColorNameValid() {
        // Given
        Color color = Color.YELLOW;

        // When
        String name = getColorName(color);

        // Then
        assertEquals("yellow", name.toLowerCase()); // Standard names might be lower/upper case
    }

    @Test
    @DisplayName("getColorName: Should throw exception for non-standard Color")
    void getColorNameNonStandard() {
        // Given
        Color customColor = new Color(1, 2, 3); // A color not defined as a public static final field in Color

        // When & Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            getColorName(customColor);
        });
        assertTrue(exception.getMessage().contains(customColor.toString()));
    }

    @Test
    @DisplayName("parseStringAsInteger: Should parse valid integer string")
    void parseStringAsIntegerValid() {
        // Given
        String intStr = " 123 ";

        // When
        Optional<Integer> result = parseStringAsInteger(intStr);

        // Then
        assertTrue(result.isPresent());
        assertEquals(123, result.get());
    }

    @Test
    @DisplayName("parseStringAsInteger: Should return empty for invalid string")
    void parseStringAsIntegerInvalid() {
        // Given
        String invalidStr = "abc";

        // When
        Optional<Integer> result = parseStringAsInteger(invalidStr);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseStringAsInteger: Should return empty for null")
    void parseStringAsIntegerNull() {
        // When
        Optional<Integer> result = parseStringAsInteger(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseStringAsInteger: Should return empty for blank string")
    void parseStringAsIntegerBlank() {
        // Given
        String blankStr = "   ";

        // When
        Optional<Integer> result = parseStringAsInteger(blankStr);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseStringAsDouble: Should parse valid double string")
    void parseStringAsDoubleValid() {
        // Given
        String doubleStr = " 123.45 ";

        // When
        Optional<Double> result = parseStringAsDouble(doubleStr);

        // Then
        assertTrue(result.isPresent());
        assertEquals(123.45, result.get());
    }

    @Test
    @DisplayName("parseStringAsDouble: Should return empty for invalid string")
    void parseStringAsDoubleInvalid() {
        // Given
        String invalidStr = "abc.def";

        // When
        Optional<Double> result = parseStringAsDouble(invalidStr);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseStringAsDouble: Should return empty for null")
    void parseStringAsDoubleNull() {
        // When
        Optional<Double> result = parseStringAsDouble(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseStringAsDouble: Should return empty for blank string")
    void parseStringAsDoubleBlank() {
        // Given
        String blankStr = "   ";

        // When
        Optional<Double> result = parseStringAsDouble(blankStr);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("isBlank: Should return true for null")
    void isBlankNull() {
        assertTrue(isBlank(null));
    }

    @Test
    @DisplayName("isBlank: Should return true for empty string")
    void isBlankEmpty() {
        assertTrue(isBlank(""));
    }

    @Test
    @DisplayName("isBlank: Should return true for blank string")
    void isBlankBlank() {
        assertTrue(isBlank("   "));
    }

    @Test
    @DisplayName("isBlank: Should return false for non-blank string")
    void isBlankNotBlank() {
        assertFalse(isBlank("abc"));
    }

    @Test
    @DisplayName("isNotBlank: Should return false for null")
    void isNotBlankNull() {
        assertFalse(isNotBlank(null));
    }

    @Test
    @DisplayName("isNotBlank: Should return false for empty string")
    void isNotBlankEmpty() {
        assertFalse(isNotBlank(""));
    }

    @Test
    @DisplayName("isNotBlank: Should return false for blank string")
    void isNotBlankBlank() {
        assertFalse(isNotBlank("   "));
    }

    @Test
    @DisplayName("isNotBlank: Should return true for non-blank string")
    void isNotBlankNotBlank() {
        assertTrue(isNotBlank("abc"));
    }

    @Test
    @DisplayName("getScreenSize: Should return correct dimension from Toolkit")
    void getScreenSizeReturnsCorrectly() {
        // Given
        // Toolkit mock is set up in @BeforeEach

        // When
        Dimension size = getScreenSize();

        // Then
        assertEquals(mockScreenSize, size);
        verify(mockToolkit).getScreenSize();
    }

    @Test
    @DisplayName("getMouseLocation: Should return correct location from MouseInfo")
    void getMouseLocationReturnsCorrectly() {
        // Given
        Point expectedLocation = new Point(100, 150); // Set in @BeforeEach

        // When
        Point location = getMouseLocation();

        // Then
        assertEquals(expectedLocation, location);
        verify(mockPointerInfo).getLocation();
    }

    @Test
    @DisplayName("getScaledScreenLocationCoordinates: Should return same coords when scale is 1")
    void getScaledScreenLocationCoordinatesNoScale() {
        // Given
        Point physicalCoords = new Point(200, 300);
        when(mockAffineTransform.getScaleX()).thenReturn(1.0);
        when(mockAffineTransform.getScaleY()).thenReturn(1.0);

        // When
        Point scaledCoords = getScaledScreenLocationCoordinates(physicalCoords);

        // Then
        assertEquals(physicalCoords, scaledCoords);
    }

    @Test
    @DisplayName("getScaledScreenLocationCoordinates: Should return scaled coords when scale is not 1")
    void getScaledScreenLocationCoordinatesWithScale() {
        // Given
        Point physicalCoords = new Point(300, 450);
        when(mockAffineTransform.getScaleX()).thenReturn(SCALE_FACTOR);
        when(mockAffineTransform.getScaleY()).thenReturn(SCALE_FACTOR);
        Point expectedScaledCoords = new Point((int) (300 / SCALE_FACTOR), (int) (450 / SCALE_FACTOR));

        // When
        Point scaledCoords = getScaledScreenLocationCoordinates(physicalCoords);

        // Then
        assertEquals(expectedScaledCoords, scaledCoords);
    }

    @Test
    @DisplayName("getScaledBoundingBox: Should return same box when scale is 1")
    void getScaledBoundingBoxNoScale() {
        // Given
        Rectangle originalBox = new Rectangle(50, 60, 100, 120);
        when(mockAffineTransform.getScaleX()).thenReturn(1.0);
        when(mockAffineTransform.getScaleY()).thenReturn(1.0);

        // When
        Rectangle scaledBox = getScaledBoundingBox(originalBox);

        // Then
        assertEquals(originalBox, scaledBox);
    }

    @Test
    @DisplayName("getScaledBoundingBox: Should return scaled box when scale is not 1")
    void getScaledBoundingBoxWithScale() {
        // Given
        Rectangle originalBox = new Rectangle(60, 90, 150, 180);
        when(mockAffineTransform.getScaleX()).thenReturn(SCALE_FACTOR);
        when(mockAffineTransform.getScaleY()).thenReturn(SCALE_FACTOR);
        Rectangle expectedScaledBox = new Rectangle(
                (int) (60 / SCALE_FACTOR),
                (int) (90 / SCALE_FACTOR),
                (int) (150 / SCALE_FACTOR),
                (int) (180 / SCALE_FACTOR)
        );

        // When
        Rectangle scaledBox = getScaledBoundingBox(originalBox);

        // Then
        assertEquals(expectedScaledBox, scaledBox);
    }

    @Test
    @DisplayName("getPhysicalScreenLocationCoordinates: Should return same coords when scale is 1")
    void getPhysicalScreenLocationCoordinatesNoScale() {
        // Given
        Point scaledCoords = new Point(200, 300);
        when(mockAffineTransform.getScaleX()).thenReturn(1.0);
        when(mockAffineTransform.getScaleY()).thenReturn(1.0);

        // When
        Point physicalCoords = getPhysicalScreenLocationCoordinates(scaledCoords);

        // Then
        assertEquals(scaledCoords, physicalCoords);
    }

    @Test
    @DisplayName("getPhysicalScreenLocationCoordinates: Should return physical coords when scale is not 1")
    void getPhysicalScreenLocationCoordinatesWithScale() {
        // Given
        Point scaledCoords = new Point(200, 300);
        when(mockAffineTransform.getScaleX()).thenReturn(SCALE_FACTOR);
        when(mockAffineTransform.getScaleY()).thenReturn(SCALE_FACTOR);
        Point expectedPhysicalCoords = new Point((int) (200 * SCALE_FACTOR), (int) (300 * SCALE_FACTOR));

        // When
        Point physicalCoords = getPhysicalScreenLocationCoordinates(scaledCoords);

        // Then
        assertEquals(expectedPhysicalCoords, physicalCoords);
    }
}