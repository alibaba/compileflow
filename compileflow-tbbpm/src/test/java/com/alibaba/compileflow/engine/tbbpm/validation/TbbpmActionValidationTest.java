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
package com.alibaba.compileflow.engine.tbbpm.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.validation.ValidationFailure;
import com.alibaba.compileflow.engine.tbbpm.model.ScriptTaskNode;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.WhileNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import com.alibaba.compileflow.engine.tbbpm.model.WaitEventTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.WaitTaskNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TbbpmActionValidationTest {
    private final TbbpmModelValidator validator = new TbbpmModelValidator();

    @Test
    void rejectsInvalidJavaTargetBeforeCodeGeneration() {
        assertFailure(flowWithAction("java", "class=\"invalid/class\" method=\"not-valid\"", ""), "invalid class");
        assertFailure(flowWithAction("java", "class=\"invalid/class\" method=\"not-valid\"", ""), "invalid Java method");
    }

    @Test
    void rejectsBlankScriptSource() {
        assertFailure(flowWithAction("script", "language=\"custom-script\"", "<code>  " + "</code>"),
                "non-blank script source");
    }

    @Test
    void treatsAtPrefixedDefaultsAsDataLiterals() {
        TbbpmModel stringDefault = parse(
                """
            <bpm code="test.string.default">
              <var name="value" dataType="java.lang.String" inOutType="param" defaultValue="@name"/>
              <start id="start"><transition to="end"/></start>
              <end id="end"/>
            </bpm>
            """);

        assertThat(validator.validate(stringDefault)).isEmpty();
        assertFailure("""
            <bpm code="test.integer.default">
              <var name="value" dataType="java.lang.Integer" inOutType="param" defaultValue="@name"/>
              <start id="start"><transition to="end"/></start>
              <end id="end"/>
            </bpm>
            """,
                "invalid defaultValue");
    }

    @Test
    void rejectsActionTasksWithoutAnAction() {
        assertFailure("""
            <bpm code="test.empty.task">
                <start id="start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <autoTask id="task" g="60,0,100,40">
                    <transition to="end"/>
                </autoTask>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """,
                "autoTask must declare one action");
    }

    @Test
    void rejectsEmbeddedBpmWithoutExactBoundaries() {
        assertFailure("""
            <bpm code="test.embedded.boundaries">
              <start id="start"><transition to="scope"/></start>
              <subBpm id="scope">
                <start id="scopeStart"><transition to="task"/></start>
                <scriptTask id="task">
                  <action type="script" language="java"><code>int value = 1;</code>
                  </action>
                </scriptTask>
                <transition to="end"/>
              </subBpm>
              <end id="end"/>
            </bpm>
            """,
                "subBpm must contain exactly one end node");
    }

    @Test
    void rejectsContextMappingOnProcessVariables() {
        assertThatThrownBy(() -> parse(
                """
            <bpm code="test.root.mapping">
                <input target="input" dataType="java.lang.String"
                     source="request.value"/>
                <start id="start" g="0,0,32,32">
                    <transition to="end"/>
                </start>
                <end id="end" g="120,0,32,32"/>
            </bpm>
            """))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(ErrorCode.CF_VALIDATION_002));
    }

    @Test
    void allowsExclusiveRoutingByDeclarationOrderAtLoopEntry() {
        TbbpmModel model = parse(
                """
            <bpm code="test.loop.entry.declaration-order">
                <var name="items" dataType="java.util.List&lt;java.lang.Integer&gt;"
                     inOutType="param"/>
                <start id="start" g="0,0,32,32">
                    <transition to="loop"/>
                </start>
                <foreach id="loop" collection="items"
                             item="item" itemType="java.lang.Integer"
                             g="60,0,300,180">
                    <start id="bodyStart"><transition to="route"/></start>
                    <exclusive id="route" g="80,40,48,48">
                        <transition to="selected"
                                    condition="item &gt; 0"/>
                        <transition to="fallback"/>
                    </exclusive>
                    <scriptTask id="selected" g="160,10,100,40">
                        <action type="script" language="java">
                                <code>int selected = item;</code>

                        </action>
                        <transition to="bodyEnd"/>
                    </scriptTask>
                    <scriptTask id="fallback" g="160,90,100,40">
                        <action type="script" language="java">
                                <code>int fallback = item;</code>

                        </action>
                        <transition to="bodyEnd"/>
                    </scriptTask>
                    <scriptTask id="bodyEnd" g="280,50,100,40">
                        <action type="script" language="java">
                                <code>int completed = item;</code>

                        </action>
                        <transition to="loopEnd"/>
                    </scriptTask>
                    <end id="loopEnd"/>
                    <transition to="end"/>
                </foreach>
                <end id="end" g="420,0,32,32"/>
            </bpm>
            """);

        List<ValidationFailure> failures = validator.validate(model);
        assertThat(failures.isEmpty())
            .withFailMessage(() -> "unexpected validation failures: " + failures)
            .isTrue();
    }

    @Test
    void rejectsAmbiguousOrIgnoredGatewayRouting() {
        assertFailure("""
            <bpm code="test.multiple.defaults">
                <start id="start" g="0,0,32,32">
                    <transition to="decision"/>
                </start>
                <exclusive id="decision" g="60,0,48,48">
                    <transition to="end"/>
                    <transition to="end"/>
                </exclusive>
                <end id="end" g="160,0,32,32"/>
            </bpm>
            """,
                "multiple default transitions");
        assertFailure("""
            <bpm code="test.parallel.condition">
                <start id="start" g="0,0,32,32">
                    <transition to="parallel"/>
                </start>
                <parallel id="parallel" g="60,0,48,48">
                    <transition to="end" condition="true"/>
                    <transition to="end"/>
                </parallel>
                <end id="end" g="160,0,32,32"/>
            </bpm>
            """,
                "Parallel gateway should not have conditional outgoing transitions");
    }

    @Test
    void acceptsRepeatedInclusiveConditionsBecauseAllMatchesAreActivated() {
        TbbpmModel model = parse(
                """
            <bpm code="test.inclusive.repeated-condition">
                <start id="start" g="0,0,32,32">
                    <transition to="inclusive"/>
                </start>
                <inclusive id="inclusive" g="60,0,48,48">
                    <transition to="join" condition="true"/>
                    <transition to="join" condition="true"/>
                </inclusive>
                <inclusive id="join" g="160,0,48,48">
                    <transition to="end"/>
                </inclusive>
                <end id="end" g="260,0,32,32"/>
            </bpm>
            """);

        List<ValidationFailure> messages = validator.validate(model);
        assertThat(messages.isEmpty())
            .withFailMessage(() -> "Expected repeated inclusive conditions to be valid but got "
                    + messages.stream().map(ValidationFailure::message).toList())
            .isTrue();
    }

    @Test
    void rejectsServiceActionOnScriptTask() {
        assertFailure("""
            <bpm code="test.script.kind">
                <start id="start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <scriptTask id="task" g="60,0,100,40">
                    <action type="java" class="java.lang.String" method="valueOf">
                            <input target="value" dataType="java.lang.Integer"
                                 defaultValue="1"/>

                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """,
                "scriptTask action type must be script");
    }

    @Test
    void rejectsScriptActionOnAutoTask() {
        assertFailure("""
            <bpm code="test.auto.kind">
                <start id="start"><transition to="task"/></start>
                <autoTask id="task">
                    <action type="script" language="qlexpress"><code>1</code></action>
                    <transition to="end"/>
                </autoTask>
                <end id="end"/>
            </bpm>
            """,
                "autoTask action type must be java or spring-bean");
    }

    @Test
    void acceptsInvocationPolicyForExplicitScript() {
        TbbpmModel model = parse(
                """
            <bpm code="test.inline.retry">
                <start id="start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <scriptTask id="task" g="60,0,100,40">
                    <action type="script" language="java">
                            <code>value++;</code>

                        <invocationPolicy maxAttempts="1"/>
                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """);

        assertThat(validator.validate(model)).isEmpty();
    }

    @Test
    void acceptsLocalManagedReplayWithoutDurableExecutionDeclaration() {
        TbbpmModel model = parse(
                """
            <bpm code="test.non.idempotent.retry">
                <start id="start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <scriptTask id="task" g="60,0,100,40">
                    <action type="script" language="qlexpress"><code>1</code>
                        <invocationPolicy maxAttempts="1"/>
                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """);

        assertThat(validator.validate(model).isEmpty()).isTrue();
    }

    @Test
    void acceptsManagedReplayForScriptAction() {
        TbbpmModel model = parse(
                """
            <bpm code="test.pure.retry">
                <start id="start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <scriptTask id="task" g="60,0,100,40">
                    <action type="script" language="qlexpress"><code>1</code>
                        <invocationPolicy maxAttempts="1"/>
                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """);

        assertThat(validator.validate(model).isEmpty()).isTrue();
    }

    @Test
    void rejectsLegacyScriptImportsAtSchemaBoundary() {
        assertThatThrownBy(() -> parse(
                flowWithAction("script", "language=\"java\"",
                        """
                <imports><import>java.util.*</import></imports>
                <code>value = 1;</code>
            """)))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsMultipleReturnVariables() {
        assertThatThrownBy(() -> parse(
                flowWithAction("script", "language=\"qlexpress\"",
                        """
                <output dataType="java.lang.Integer" target="first"/>
                <output dataType="java.lang.Integer" target="second"/>
                <code>1</code>
            """)))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsInnerVariablesInActionMappings() {
        assertThatThrownBy(() -> parse(
                flowWithAction("script", "language=\"java\"",
                        """
                <output dataType="java.lang.Integer"
                     inOutType="inner" target="value"/>
                <code>value = 1;</code>
            """)))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsInnerVariablesInSubProcessMappings() {
        assertThatThrownBy(() -> parse(
                """
            <bpm code="test.sub.mapping">
                <start id="start" g="0,0,32,32">
                    <transition to="sub"/>
                </start>
                <bpmCall id="sub" g="60,0,100,40" code="child" classpath="child.bpm">
                    <output dataType="java.lang.Integer"
                         inOutType="inner" target="value"/>
                    <transition to="end"/>
                </bpmCall>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsProcessVariableNamesThatCannotBecomeJavaFields() {
        assertFailure("""
            <bpm code="test.invalid.variable">
                <var name="bad-name" dataType="java.lang.String"
                     inOutType="param"/>
                <start id="start" g="0,0,32,32">
                    <transition to="end"/>
                </start>
                <end id="end" g="80,0,32,32"/>
            </bpm>
            """,
                "invalid Java name");
    }

    @Test
    void allowsSameVariableNameForInputAndReturnMapping() {
        TbbpmModel model = parse(
                """
            <bpm code="test.same.mapping">
                <var name="value" dataType="java.lang.Integer"
                     inOutType="return"/>
                <start id="start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <scriptTask id="task" g="60,0,100,40">
                    <action type="script" language="qlexpress">
                        <input source="value" target="value" dataType="java.lang.Integer"/>
                        <output target="value" dataType="java.lang.Integer"/>
                        <code>value + 1</code>
                    </action>
                    <transition to="end"/>
                </scriptTask>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """);

        assertThat(validator
            .validate(model)
            .stream()
            .noneMatch(failure -> failure.message().contains("duplicate"))).isTrue();
    }

    @Test
    void rejectsUnknownActionReturnTargetBeforeCodeGeneration() {
        assertFailure(flowWithAction("script", "language=\"qlexpress\"",
                        """
                <output dataType="java.lang.Integer" target="missing"/>
                <code>1</code>
            """),
                "output target must reference a declared process variable");
    }

    @Test
    void rejectsNonCanonicalActionIdentitiesInProgrammaticModels() {
        TbbpmModel model = parse(
                flowWithAction("script", "language=\"qlexpress\"",
                        """
            <input target="value" dataType="java.lang.String" defaultValue="sample"/>
            <code>value</code>
            """));
        ScriptTaskNode task = (ScriptTaskNode) model.getNode("task");
        task.getAction().setLanguage("QLExpress");
        task.getAction().getInputMappings().get(0).setDataType(" java.lang.String ");

        assertThat(validator.validate(model))
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("lowercase kebab-case"))
            .anyMatch(message -> message.contains("dataType must not contain surrounding whitespace"));
    }

    @Test
    void rejectsMappingDefaultsThatGeneratedCodeWouldIgnore() {
        assertFailure(flowWithAction("script", "language=\"qlexpress\"",
                        """
                <input target="value" dataType="java.lang.Integer" source="value"
                     defaultValue="1"/>
                <code>value</code>
            """),
                "must declare exactly one of source or defaultValue");
        assertThatThrownBy(() -> parse(
                flowWithAction("script", "language=\"qlexpress\"",
                        """
                <output dataType="java.lang.Integer" target="result"
                     defaultValue="1"/>
                <code>1</code>
            """)))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void allowsActionReturnValueToBeDiscardedByOmittingOutputMapping() {
        TbbpmModel model = parse(flowWithAction("script", "language=\"qlexpress\"", """
                <code>1</code>
            """));

        assertThat(validator
            .validate(model)
            .stream()
            .noneMatch(failure -> failure.message().contains("return variable")))
            .isTrue();
    }

    @Test
    void rejectsSubProcessReturnWithoutParentTarget() {
        assertThatThrownBy(() -> parse(
                """
            <bpm code="test.sub.return.mapping">
                <start id="start" g="0,0,32,32">
                    <transition to="sub"/>
                </start>
                <bpmCall id="sub" g="60,0,100,40" code="child" classpath="child.bpm">
                    <output source="result"/>
                    <transition to="end"/>
                </bpmCall>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsSubProcessOutputsMappedToTheSameParentVariable() {
        assertFailure("""
            <bpm code="test.sub.duplicate.output">
                <var name="result" dataType="java.lang.Integer"
                     inOutType="return"/>
                <start id="start" g="0,0,32,32">
                    <transition to="sub"/>
                </start>
                <bpmCall id="sub" g="60,0,100,40"
                        code="child" classpath="child.bpm">
                    <output source="first" target="result"/>
                    <output source="second" target="result"/>
                    <transition to="end"/>
                </bpmCall>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """,
                "duplicate output target: result");
    }

    @Test
    void rejectsDuplicateActionsInsteadOfSilentlyReplacingOne() {
        assertThatThrownBy(() -> parse(
                """
            <bpm code="test.duplicate.action">
                <scriptTask id="task" g="0,0,100,40">
                    <action type="script" language="qlexpress"><code>1</code></action>
                    <action type="script" language="qlexpress"><code>2</code></action>
                </scriptTask>
            </bpm>
            """))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(ErrorCode.CF_VALIDATION_002));
    }

    @Test
    void rejectsActionOnGatewayAtTheSchemaBoundary() {
        assertThatThrownBy(() -> parse(
                """
            <bpm code="test.gateway.action">
                <exclusive id="route" g="0,0,40,40">
                    <action type="script" language="java">
                            <code>value = 1;</code>

                    </action>
                </exclusive>
            </bpm>
            """))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsActionOnSubProcessInsteadOfSilentlySkippingIt() {
        assertThatThrownBy(() -> parse(
                """
            <bpm code="test.sub.action">
                <start id="start" g="0,0,32,32">
                    <transition to="sub"/>
                </start>
                <bpmCall id="sub" g="60,0,100,40"
                        code="child" classpath="child.bpm">
                    <action type="script" language="java">
                            <code>value = 1;</code>

                    </action>
                    <transition to="end"/>
                </bpmCall>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsConditionOnJoinGatewayInsteadOfSilentlyIgnoringIt() {
        assertFailure("""
            <bpm code="test.join.condition">
                <start id="start" g="0,0,32,32">
                    <transition to="split"/>
                </start>
                <exclusive id="split" g="60,0,40,40">
                    <transition to="left" condition="true"/>
                    <transition to="right"/>
                </exclusive>
                <autoTask id="left" g="120,0,100,40">
                    <transition to="join"/>
                </autoTask>
                <autoTask id="right" g="120,60,100,40">
                    <transition to="join"/>
                </autoTask>
                <exclusive id="join" g="260,30,40,40">
                    <transition to="end" condition="true"/>
                </exclusive>
                <end id="end" g="340,30,32,32"/>
            </bpm>
            """,
                "Join gateway must not have a conditional outgoing transition");
    }

    @Test
    void rejectsBlankWaitEventInProgrammaticModel() {
        TbbpmModel model = parse(
                """
            <bpm code="test.wait-event.validation">
                <start id="start" g="0,0,32,32">
                    <transition to="wait"/>
                </start>
                <waitEventTask id="wait"
                               event="approved" g="60,0,100,40">
                    <transition to="end"/>
                </waitEventTask>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """);
        ((WaitEventTaskNode) model.getNode("wait")).setEvent(" ");

        List<ValidationFailure> messages = validator.validate(model);

        assertThat(messages
            .stream()
            .map(ValidationFailure::message)
            .anyMatch(message -> message.contains("must declare a non-blank event")))
            .isTrue();
    }

    @Test
    void rejectsInvalidWaitTimeoutInProgrammaticModel() {
        TbbpmModel model = parse(
                """
            <bpm code="test.wait-deadline.validation">
                <start id="start" g="0,0,32,32"><transition to="wait"/></start>
                <waitEventTask id="wait" event="approved" timeout="PT1M" g="60,0,100,40">
                    <transition to="end"/>
                </waitEventTask>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """);
        ((WaitEventTaskNode) model.getNode("wait")).setTimeout("tomorrow");

        List<ValidationFailure> messages = validator.validate(model);

        assertThat(messages
            .stream()
            .map(ValidationFailure::message)
            .anyMatch(message -> message.contains("timeout must be a non-negative ISO-8601")))
            .isTrue();
    }

    @Test
    void rejectsNonCanonicalWaitTimeoutInProgrammaticModel() {
        TbbpmModel model = parse(
                """
            <bpm code="test.wait-deadline.canonical">
                <start id="start" g="0,0,32,32"><transition to="wait"/></start>
                <waitTask id="wait" timeout="PT1M" g="60,0,100,40">
                    <transition to="end"/>
                </waitTask>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """);
        ((WaitTaskNode) model.getNode("wait")).setTimeout("pt1m");

        assertThat(validator.validate(model))
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("canonical uppercase ISO-8601 notation"));
    }

    @Test
    void parallelForEachAcceptsOptionalPairedOutputAggregation() {
        TbbpmModel valid = parse(parallelLoopFlow("execution=\"parallel\"", true));

        assertThat(validator.validate(valid)).isEmpty();
        assertThat(validator.validate(parse(parallelLoopFlow("execution=\"parallel\"", false)))).isEmpty();
    }

    @Test
    void sequentialForEachAcceptsOutputAggregation() {
        TbbpmModel model = parse(parallelLoopFlow("", true));

        assertThat(validator.validate(model)).isEmpty();
    }

    @Test
    void rejectsForEachOutputElementThatIsNotInnerState() {
        TbbpmModel model = parse(parallelLoopFlow("", true)
            .replace("name=\"slot\" dataType=\"java.lang.String\" inOutType=\"inner\"",
                    "name=\"slot\" dataType=\"java.lang.String\" inOutType=\"return\""));

        assertThat(validator.validate(model))
            .extracting(ValidationFailure::message)
            .anyMatch(message -> message.contains("output.source must reference an inner process variable"));
    }

    @Test
    void rejectsNonPositiveMaxIterationsInProgrammaticModel() {
        TbbpmModel model = parse(loopFlow("while", "condition=\"false\" maxIterations=\"1\""));
        ((WhileNode) model.getNode("loop")).setMaxIterations(0);

        List<ValidationFailure> messages = validator.validate(model);

        assertThat(messages
            .stream()
            .map(ValidationFailure::message)
            .anyMatch(message -> message.contains("maxIterations must be positive")))
            .isTrue();
    }

    @Test
    void schemaPreservesPaddedExpressionsButRejectsBlankExpressions() {
        TbbpmModel model = parse(loopFlow("while", "condition=\"  false  \" maxIterations=\"1\""));

        assertThat(((WhileNode) model.getNode("loop")).getCondition()).isEqualTo("  false  ");
        assertThatThrownBy(() -> parse(loopFlow("while", "condition=\"   \" maxIterations=\"1\"")))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Flow schema validation failed");
        assertThatThrownBy(() -> parse(loopFlow("while", "condition=\"\u00a0\" maxIterations=\"1\"")))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Flow schema validation failed");
        assertThatThrownBy(() -> parse(loopFlow("while", "condition=\"false\" maxIterations=\"1\"")
            .replace("id=\"loop\"", "id=\"" + "n".repeat(513) + "\"")))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Flow schema validation failed");
        assertThatThrownBy(() -> parse(loopFlow("while", "condition=\"false\" maxIterations=\"1\"")
            .replace("id=\"loop\"", "id=\"loop\u202e\"")))
            .isInstanceOf(CompileFlowException.class)
            .hasMessageContaining("Flow schema validation failed");
    }

    @Test
    void rejectsUnknownForEachCollectionBeforeCodeGeneration() {
        assertFailure(loopFlow("foreach", """
                collection="missing" item="item" itemType="java.lang.Object"
                """),
                "collection must reference a declared process variable");
    }

    @Test
    void rejectsLoopVariablesThatShadowProcessState() {
        assertFailure(loopFlow("foreach", """
                collection="items" item="items" itemType="java.lang.Object"
                """),
                "item must not shadow a process");
    }

    @Test
    void rejectsDirectMutationInRoutingAndLoopConditions() {
        assertFailure("""
            <bpm code="test.mutating.route">
                <var name="flag" dataType="java.lang.Boolean"
                     inOutType="param"/>
                <start id="start" g="0,0,32,32">
                    <transition to="decision"/>
                </start>
                <exclusive id="decision" g="60,0,48,48">
                    <transition to="matched" condition="flag = true"/>
                    <transition to="fallback"/>
                </exclusive>
                <end id="matched" g="160,0,32,32"/>
                <end id="fallback" g="160,80,32,32"/>
            </bpm>
            """,
                "must be side-effect free");
        assertFailure(loopFlow("while", "condition=\"items.clear() || (flag = true)\" maxIterations=\"10\""),
                "must be side-effect free");
    }

    @Test
    void rejectsExpressionLanguageWrappersBeforeCodeGeneration() {
        assertFailure("""
            <bpm code="test.wrapped.route">
                <var name="flag" dataType="java.lang.Boolean"
                     inOutType="param"/>
                <start id="start" g="0,0,32,32">
                    <transition to="decision"/>
                </start>
                <exclusive id="decision" g="60,0,48,48">
                    <transition to="matched" condition="${flag}"/>
                    <transition to="fallback"/>
                </exclusive>
                <end id="matched" g="160,0,32,32"/>
                <end id="fallback" g="160,80,32,32"/>
            </bpm>
            """,
                "must contain the raw Java expression body");
    }

    @Test
    void acceptsConcurrentSplitInsideLoopAsSourceSemantics() {
        List<ValidationFailure> failures = validator.validate(
                parse(
                        """
            <bpm code="test.loop.concurrent">
                <start id="start" g="0,0,32,32">
                    <transition to="loop"/>
                </start>
                <while id="loop" condition="true"
                             g="60,0,260,180" maxIterations="100">
                    <start id="loopStart"><transition to="fork"/></start>
                    <parallel id="fork" g="80,20,40,40">
                        <transition to="left"/>
                        <transition to="right"/>
                    </parallel>
                    <autoTask id="left" g="140,0,80,40">
                        <transition to="join"/>
                    </autoTask>
                    <autoTask id="right" g="140,80,80,40">
                        <transition to="join"/>
                    </autoTask>
                    <parallel id="join" g="240,40,40,40">
                        <transition to="bodyEnd"/>
                    </parallel>
                    <autoTask id="bodyEnd" g="300,40,80,40"><transition to="loopEnd"/></autoTask>
                    <end id="loopEnd"/>
                    <transition to="end"/>
                </while>
                <end id="end" g="360,0,32,32"/>
            </bpm>
            """));

        assertThat(failures)
            .extracting(ValidationFailure::message)
            .noneMatch(message -> message.contains("Parallel and inclusive splits inside a loop"));
    }

    @Test
    void rejectsImplicitRoutingAndNonTerminalLoopEndDuringModelValidation() {
        assertFailure("""
            <bpm code="test.implicit.routing">
                <start id="start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <autoTask id="task" g="60,0,100,40">
                    <transition to="left"/>
                    <transition to="right"/>
                </autoTask>
                <end id="left" g="200,0,32,32"/>
                <end id="right" g="200,80,32,32"/>
            </bpm>
            """,
                "Non-gateway node must not branch");
        assertThatThrownBy(() -> parse(
                """
            <bpm code="test.loop.end.outgoing">
                <start id="start" g="0,0,32,32">
                    <transition to="loop"/>
                </start>
                <while id="loop" condition="true"
                             g="60,0,180,120" maxIterations="100">
                    <start id="loopStart"><transition to="body"/></start>
                    <autoTask id="body" g="80,20,100,40">
                        <transition to="tail"/>
                    </autoTask>
                    <autoTask id="tail" g="200,20,100,40"/>
                    <end id="loopEnd"><transition to="tail"/></end>
                    <transition to="end"/>
                </while>
                <end id="end" g="280,0,32,32"/>
            </bpm>
            """))
            .isInstanceOf(CompileFlowException.class);
    }

    @Test
    void rejectsConflictingCalledProcessTargets() {
        assertFailure("""
            <bpm code="test.call.binding">
                <start id="start" g="0,0,32,32">
                    <transition to="child"/>
                </start>
                <bpmCall id="child" code="child.flow"
                        classpath="child/flow.bpm" version="v1"
                        g="60,0,100,40">
                    <transition to="end"/>
                </bpmCall>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """,
                "exactly one of classpath or version is required");
    }

    @Test
    void acceptsDiagramNoteInsideLoop() {
        TbbpmModel model = parse(
                """
            <bpm code="test.loop.note">
              <start id="start"><transition to="loop"/></start>
              <while id="loop" condition="true" maxIterations="1">
                <start id="loopStart"><transition to="body"/></start>
                <scriptTask id="body">
                  <action type="script" language="java"><code>int value = 1;</code>
                  </action>
                  <transition to="loopEnd"/>
                </scriptTask>
                <end id="loopEnd"/>
                <note id="annotation" comment="implementation detail"/>
                <transition to="end"/>
              </while>
              <end id="end"/>
            </bpm>
            """);

        assertThat(validator.validate(model)).isEmpty();
    }

    @Test
    void allowsLoopControlInsideEmbeddedScopeOfLoop() {
        TbbpmModel model = parse(
                """
            <bpm code="test.loop.embedded.control">
              <start id="start"><transition to="loop"/></start>
              <while id="loop" condition="true" maxIterations="2">
                <start id="loopStart"><transition to="scope"/></start>
                <subBpm id="scope">
                  <start id="scopeStart"><transition to="stop"/></start>
                  <break id="stop" condition="true"><transition to="scopeEnd"/></break>
                  <end id="scopeEnd"/>
                  <transition to="loopEnd"/>
                </subBpm>
                <end id="loopEnd"/>
                <transition to="end"/>
              </while>
              <end id="end"/>
            </bpm>
            """);

        assertThat(validator.validate(model)).isEmpty();
    }

    @Test
    void rejectsLoopControlInsideEmbeddedScopeWithoutEnclosingLoop() {
        assertFailure("""
            <bpm code="test.embedded.control.without.loop">
              <start id="start"><transition to="scope"/></start>
              <subBpm id="scope">
                <start id="scopeStart"><transition to="stop"/></start>
                <break id="stop" condition="true"><transition to="scopeEnd"/></break>
                <end id="scopeEnd"/>
                <transition to="end"/>
              </subBpm>
              <end id="end"/>
            </bpm>
            """,
                "break node must be contained by a loop");
    }

    @Test
    void rejectsBreakInsideEmbeddedScopeOfParallelForEach() {
        assertFailure("""
            <bpm code="test.parallel.loop.embedded.break">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <start id="start"><transition to="loop"/></start>
              <foreach id="loop" execution="parallel" collection="items"
                           item="item" itemType="java.lang.String">
                <start id="loopStart"><transition to="scope"/></start>
                <subBpm id="scope">
                  <start id="scopeStart"><transition to="stop"/></start>
                  <break id="stop" condition="true"><transition to="scopeEnd"/></break>
                  <end id="scopeEnd"/>
                  <transition to="loopEnd"/>
                </subBpm>
                <end id="loopEnd"/>
                <transition to="end"/>
              </foreach>
              <end id="end"/>
            </bpm>
            """,
                "Parallel foreach does not support break");
    }

    private void assertFailure(String xml, String expectedMessage) {
        TbbpmModel model = parse(xml);
        List<ValidationFailure> messages = validator.validate(model);
        assertThat(messages
            .stream()
            .map(ValidationFailure::message)
            .anyMatch(message -> message.contains(expectedMessage)))
            .withFailMessage(() -> "Expected validation message containing '" + expectedMessage + "' but got "
                    + messages.stream().map(ValidationFailure::message).toList())
            .isTrue();
    }

    private TbbpmModel parse(String xml) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("test.action.validation", xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String flowWithAction(String actionType, String actionAttributes, String actionBody) {
        String taskType = "script".equals(actionType) ? "scriptTask" : "autoTask";
        return """
            <bpm code="test.action.validation">
                <start id="start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <%s id="task" g="60,0,100,40">
                    <action type="%s" %s>
                        %s
                    </action>
                    <transition to="end"/>
                </%s>
                <end id="end" g="200,0,32,32"/>
            </bpm>
            """
            .formatted(taskType, actionType, actionAttributes, actionBody, taskType);
    }

    private String parallelLoopFlow(String loopAttributes, boolean output) {
        return """
            <bpm code="test.parallel.loop">
              <var name="items" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="param"/>
              <var name="slot" dataType="java.lang.String" inOutType="inner"/>
              <var name="results" dataType="java.util.List&lt;java.lang.String&gt;" inOutType="return"/>
              <start id="start" g="0,0,32,32"><transition to="loop"/></start>
              <foreach id="loop" %s
                   collection="items" item="item" itemType="java.lang.String"
                   g="40,0,220,120">
                %s
                <transition to="end"/>
                <start id="bodyStart"><transition to="body"/></start>
                <scriptTask id="body" g="70,20,100,40">
                  <action type="script" language="java"><code>slot = item;</code>
                  </action>
                  <transition to="bodyEnd"/>
                </scriptTask>
                <end id="bodyEnd"/>
              </foreach>
              <end id="end" g="300,0,32,32"/>
            </bpm>
            """
            .formatted(loopAttributes, output ? "<output target=\"results\" source=\"slot\"/>" : "");
    }

    private String loopFlow(String element, String attributes) {
        return """
            <bpm code="test.loop.validation">
                <var name="items" dataType="java.util.List"
                     inOutType="param"/>
                <start id="start" g="0,0,32,32">
                    <transition to="loop"/>
                </start>
                <%s id="loop" %s g="60,0,180,120">
                    <start id="bodyStart"><transition to="body"/></start>
                    <scriptTask id="body" g="80,20,100,40">
                        <action type="script" language="java">
                                <code>int value = 1;</code>

                        </action>
                        <transition to="bodyEnd"/>
                    </scriptTask>
                    <end id="bodyEnd"/>
                    <transition to="end"/>
                </%s>
                <end id="end" g="280,0,32,32"/>
            </bpm>
            """
            .formatted(element, attributes, element);
    }
}
