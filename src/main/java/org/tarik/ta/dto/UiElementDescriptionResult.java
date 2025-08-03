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

@JsonClassDescription("the extracted by you information about the target UI element")
public record UiElementDescriptionResult(
        @JsonFieldDescription("contains the identified by you name of the target element.")
        String name,

        @JsonFieldDescription("contains the extracted by you information which describes the target element itself.")
        String ownDescription,

        @JsonFieldDescription("contains the extracted by you information about UI elements which are located nearby the target element.")
        String anchorsDescription,

        @JsonFieldDescription("contains a very short summary of the view in which the element is located.")
        String pageSummary) {
}