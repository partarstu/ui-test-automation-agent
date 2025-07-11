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

package org.tarik.ta.dto;

import org.tarik.ta.annotations.JsonClassDescription;
import org.tarik.ta.annotations.JsonFieldDescription;

import java.awt.*;

@JsonClassDescription("a single bounding box with coordinates.")
public record BoundingBox(
 @JsonFieldDescription("The x-coordinate of the top-left corner of the bounding box.") int x1,
 @JsonFieldDescription("The y-coordinate of the top-left corner of the bounding box.") int y1,
 @JsonFieldDescription("The x-coordinate of the bottom-right corner of the bounding box.") int x2,
 @JsonFieldDescription("The y-coordinate of the bottom-right corner of the bounding box.") int y2
) {
    public Rectangle getActualBoundingBox(int actualImageWidth, int actualImageHeight) {
        var actualX1 =         x1*actualImageWidth/1000;
        var actualY1 =         y1*actualImageHeight/1000;
        var actualX2 =         x2*actualImageWidth/1000;
        var actualY2 =         y2*actualImageHeight/1000;
        return new Rectangle(actualX1, actualY1, actualX2-actualX1, actualY2-actualY1);
    }
}
