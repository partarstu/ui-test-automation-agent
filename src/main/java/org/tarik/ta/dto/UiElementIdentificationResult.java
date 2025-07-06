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

@JsonClassDescription("the identified best match for a target UI element")
public record UiElementIdentificationResult(
        @JsonFieldDescription("indicates whether there is at least one good match. Must be \"false\", if you're sure that there is" +
                " no UI element candidate which matches well based on its info and visual characteristics to the provided to you " +
                "description of target UI element, \"true\" otherwise.")
        boolean success,
        @JsonFieldDescription("contains the ID of the identified best matching UI element candidate. If the value of \"success\" field " +
                "is \"false\", this field must be an empty string, \"\".")
        String elementId,
        @JsonFieldDescription("contains any comments regarding the results of identification. If the value of \"success\" field is " +
                "\"true\", this field should have your comments clarifying why a specific UI element was identified as the best match " +
                "comparing to others. If the value of \"success\" field is \"false\", this field should have your comments clarifying " +
                "why you found no good match at all.")
        String message) {
}