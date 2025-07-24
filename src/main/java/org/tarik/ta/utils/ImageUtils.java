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

import dev.langchain4j.data.image.Image;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import static java.nio.file.Files.createDirectories;
import static java.time.LocalDateTime.now;
import static java.time.format.DateTimeFormatter.ofPattern;
import static javax.imageio.ImageIO.write;

import org.tarik.ta.AgentConfig;

public class ImageUtils {
    private static final String SCREENSHOTS_SAVE_FOLDER = AgentConfig.getScreenshotsSaveFolder();
    private static final Logger LOG = LoggerFactory.getLogger(ImageUtils.class);

    public static Image getImage(String base64Image, String format) {
        return Image.builder()
                .mimeType("image/" + format)
                .base64Data(base64Image)
                .build();
    }

    @NotNull
    public static BufferedImage toBufferedImage(java.awt.Image image, int targetWidth, int targetHeight) {
        if (image instanceof BufferedImage result) {
            return result;
        } else {
            image.getHeight(null);
            BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D bGr = result.createGraphics();
            bGr.drawImage(image, 0, 0, null);
            bGr.dispose();
            return result;
        }
    }

    public static byte[] imageToByteArray(BufferedImage image, String formatName) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            write(image, formatName, stream);
            return stream.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Image getImage(BufferedImage bufferedImage, String format) {
        return getImage(convertImageToBase64(bufferedImage, format), format);
    }

    public static String convertImageToBase64(BufferedImage image, String format) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            write(image, format, stream);
            byte[] imageBytes = stream.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BufferedImage convertBase64ToImage(String encodedString) {
        byte[] imageBytes = Base64.getDecoder().decode(encodedString);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
            return ImageIO.read(bis);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static BufferedImage cloneImage(BufferedImage image) {
        ColorModel cm = image.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = image.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    public static boolean saveScreenshot(BufferedImage resultingScreenshot, String postfix) {
        LocalDateTime now = now();
        DateTimeFormatter formatter = ofPattern("yyyy_MM_dd_HH_mm_ss");
        String timestamp = now.format(formatter);
        var filePath = Paths.get(SCREENSHOTS_SAVE_FOLDER)
                .resolve("%s_%s.png".formatted(timestamp, postfix)).toAbsolutePath();
        try {
            createDirectories(filePath.getParent());
            write(resultingScreenshot, "png", filePath.toFile() );
            return true;
        } catch (IOException e) {
            String message = "Couldn't save screenshot %s.".formatted(filePath);
            LOG.error(message, e);
            return false;
        }
    }
}
