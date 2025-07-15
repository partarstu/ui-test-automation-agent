package org.tarik.ta.dto;

import org.tarik.ta.annotations.JsonClassDescription;
import org.tarik.ta.annotations.JsonFieldDescription;


import java.util.List;

@JsonClassDescription("the list of identified by you tool requests")
public record TestCaseExecutionPlan(
        @JsonFieldDescription("contains all identified by test step execution plans.")
        List<TestStepExecutionPlan> testStepExecutionPlans
) {
}
