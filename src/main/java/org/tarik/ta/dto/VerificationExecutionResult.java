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

@JsonClassDescription("the result of the verification")
public record VerificationExecutionResult(
        @JsonFieldDescription("indicates whether the verification succeeded (true) or failed (false).")
        boolean success,
        @JsonFieldDescription("contains a detailed description of the failure, if the verification failed. If the verification " +
                "succeeded, this field should contain the justification of the positive verification result, i.e. the explicit " +
                "description of the actual visual state and why this state means that the verification result is successful.")
        String message) {
}