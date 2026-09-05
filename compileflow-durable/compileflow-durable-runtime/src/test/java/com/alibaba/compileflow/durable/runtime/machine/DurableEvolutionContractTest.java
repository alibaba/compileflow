/*
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
package com.alibaba.compileflow.durable.runtime.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.durable.runtime.kernel.ResumePoint;
import com.alibaba.compileflow.durable.runtime.program.DurableCompilerTestSupport;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

class DurableEvolutionContractTest {
    private static TbbpmModel parse(String xml) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("durable.simple", xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String simpleWaitFlow() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="durable.simple" name="Durable Simple">
                <var name="approved" dataType="java.lang.Boolean"
                     inOutType="return"/>
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="approval"/>
                </start>
                <waitEventTask id="approval" name="Approval"
                               event="approved"
                               g="80,0,100,40">
                    <transition to="end"/>
                </waitEventTask>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """;
    }

    @Test
    void semanticPlanAndResumeCoordinatesAreStableWithoutProgramIdentity() {
        DurableMachinePlan first = DurableCompilerTestSupport.lower(parse(simpleWaitFlow()));
        DurableMachinePlan second = DurableCompilerTestSupport.lower(parse(simpleWaitFlow()));
        DurableMachinePlan changed = DurableCompilerTestSupport.lower(
                parse(simpleWaitFlow().replace("event=\"approved\"", "event=\"rejected\"")));

        assertThat(first.digest()).isEqualTo(second.digest()).isNotEqualTo(changed.digest());
        assertThat(first.semanticPlan().getDigest())
            .isEqualTo(second.semanticPlan().getDigest())
            .isNotEqualTo(changed.semanticPlan().getDigest());
        assertThat(first.resumes())
            .containsOnlyKeys(ResumePoint.beforeElement("start").key(), ResumePoint.beforeElement("approval").key(),
                    ResumePoint.beforeElement("end").key(), ResumePoint.afterElement("approval").key());
        assertThat(first.resumes()).isEqualTo(changed.resumes());
    }

    @Test
    void expressionAllowlistRejectsCallsAndMutation() {
        DurableJavaExpressionValidator validator = new DurableJavaExpressionValidator();

        Map<String, String> types = Map.of("approved", "java.lang.Boolean", "count", "java.lang.Integer");
        assertThat(validator.validate("approved && count < 3", types, DurableExpressionValidator.TargetType.BOOLEAN)).isEmpty();
        assertThat(validator.validate("clock.now() != null", Map.of("clock", "java.time.Clock"),
                DurableExpressionValidator.TargetType.BOOLEAN))
            .isNotEmpty();
        assertThat(validator.validate("count++ > 0", types, DurableExpressionValidator.TargetType.BOOLEAN))
            .anyMatch(message -> message.contains("POSTFIX_INCREMENT"));
    }

    @Test
    void expressionValidationIsTypedAndCannotEscapeItsWholeWrapper() {
        DurableJavaExpressionValidator validator = new DurableJavaExpressionValidator();

        assertThat(validator.validate("count + 1", Map.of("count", "java.lang.Integer"),
                DurableExpressionValidator.TargetType.BOOLEAN))
            .anyMatch(message -> message.contains("javac error"));
        assertThat(validator.validate("true); } static boolean injected() { return (true", Map.of(),
                DurableExpressionValidator.TargetType.BOOLEAN))
            .isNotEmpty();
        assertThat(validator.validate("java.time.Instant.now() != null", Map.of(),
                DurableExpressionValidator.TargetType.BOOLEAN))
            .isNotEmpty();
    }

    @Test
    void expressionValidationSafelyErasesValidatedGenericSlotTypes() {
        DurableJavaExpressionValidator validator = new DurableJavaExpressionValidator();

        assertThat(validator.validate("values != null && values.size() > 0",
                Map.of("values", "java.util.List<java.lang.Integer>"), DurableExpressionValidator.TargetType.BOOLEAN))
            .isEmpty();
        assertThat(validator.validate("attributes != null && attributes.containsKey(\"ready\")",
                Map.of("attributes", "java.util.Map<java.lang.String, java.lang.Boolean>"),
                DurableExpressionValidator.TargetType.BOOLEAN))
            .isEmpty();
    }

    @Test
    void boundExpressionsPreserveSourceAndRejectNormalizedBindingIdentities() {
        BoundExpression expression = new BoundExpression(" approved ", BoundExpression.ResultKind.BOOLEAN,
                List.of(
                        new BoundExpression.Binding("approved", "java.lang.Boolean",
                                BoundExpression.Binding.Source.STATE)));

        assertThat(expression.source()).isEqualTo(" approved ");
        assertThatThrownBy(() -> new BoundExpression.Binding(" approved", "java.lang.Boolean",
                BoundExpression.Binding.Source.STATE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("surrounding whitespace");
    }
}
