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

@JsonClassDescription("the identified best match")
public record UiElementIdentificationResult(
        @JsonFieldDescription("indicates whether there is a match at all. Must be \"false\", if you're sure that there is no UI element " +
                "candidate which matches visually and based on its functional info to the provided to you description, \"true\" otherwise.")
        boolean success,
        @JsonFieldDescription("contains the ID of the identified best matching UI element. If the value of \"success\" field is " +
                "\"false\", this field should be an empty string, \"\".")
        String elementId,
        @JsonFieldDescription("contains any comments regarding the identification. If the value of \"success\" field is " +
                "\"true\", this field should have the info about why a specific UI element was identified as the best match comparing to " +
                "others. If the value of \"success\" field is \"false\", this field should have the info about why there was no match at " +
                "all.")
        String message) {
}