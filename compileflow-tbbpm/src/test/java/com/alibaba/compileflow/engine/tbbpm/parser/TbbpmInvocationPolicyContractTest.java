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
package com.alibaba.compileflow.engine.tbbpm.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.model.action.InvocationPolicy;
import com.alibaba.compileflow.engine.core.model.action.RetryJitter;
import com.alibaba.compileflow.engine.tbbpm.writer.TbbpmXmlWriter;
import com.alibaba.compileflow.engine.tbbpm.semantic.TbbpmSemanticFrontend;
import com.alibaba.compileflow.engine.tbbpm.model.AutoTaskNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TbbpmInvocationPolicyContractTest {
    private static TbbpmModel parse(String xml) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("test.invocation-policy", xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String policyXml(String attributes) {
        return policyXml(attributes, "replayable");
    }

    private static String policyXml(String attributes, String execution) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="test.invocation-policy" name="Invocation Policy">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="task"/>
                </start>
                <autoTask id="task" name="Task" g="80,0,100,40">
                    <action type="java" execution="%s" class="java.lang.String" method="valueOf">
                        <invocationPolicy %s/>
                    </action>
                    <transition to="end"/>
                </autoTask>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """
            .formatted(execution, attributes);
    }

    @Test
    void strictSchemaParsesAndRoundTripsTheCanonicalPolicy() {
        TbbpmModel model = parse(
                policyXml(
                        """
            timeout="PT20S" attemptTimeout="PT5S" maxAttempts="3" initialBackoff="PT0.2S"
            backoffMultiplier="2.0" maxBackoff="PT2S"
            jitter="full" retryOn="custom-retry" onFailure="custom-failure"
            """));
        InvocationPolicy policy = ((AutoTaskNode) model.getNode("task")).getAction().getInvocationPolicy();

        assertThat(policy.getTimeout()).isEqualTo("PT20S");
        assertThat(policy.getAttemptTimeout()).isEqualTo("PT5S");
        assertThat(policy.getMaxAttempts()).isEqualTo(3);
        assertThat(policy.getInitialBackoff()).isEqualTo("PT0.2S");
        assertThat(policy.getBackoffMultiplier()).isEqualTo(2.0d);
        assertThat(policy.getMaxBackoff()).isEqualTo("PT2S");
        assertThat(policy.getJitter()).isEqualTo(RetryJitter.FULL);
        assertThat(policy.getRetryOn()).isEqualTo("custom-retry");
        assertThat(policy.getOnFailure()).isEqualTo("custom-failure");

        ByteArrayOutputStream output = (ByteArrayOutputStream) TbbpmXmlWriter.getInstance().write(model);
        String written = output.toString(StandardCharsets.UTF_8);
        assertThat(written.contains("timeout=\"PT20S\"")).isTrue();
        assertThat(written.contains("maxAttempts=\"3\"")).isTrue();
        assertThat(written.contains("backoffMultiplier=\"2.0\"")).isTrue();
        assertThat(written.contains("jitter=\"full\"")).isTrue();
        parse(written);
    }

    @Test
    void strictSchemaRejectsRemovedRetryCycleAttribute() {
        assertThatThrownBy(() -> parse(policyXml("retry=\"R3/PT1S\""))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void strictSchemaRejectsExcessiveMaxAttempts() {
        assertThatThrownBy(() -> parse(policyXml("maxAttempts=\"101\""))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void strictSchemaRejectsInvalidBackoffMultipliers() {
        assertThatThrownBy(() -> parse(policyXml("backoffMultiplier=\"0.5\""))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> parse(policyXml("backoffMultiplier=\"NaN\""))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> parse(policyXml("backoffMultiplier=\"INF\""))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void strictSchemaRejectsUnknownRetryJitter() {
        assertThatThrownBy(() -> parse(policyXml("jitter=\"decorrelated\""))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsDurationsThatCannotBeRepresentedAsWholeMilliseconds() {
        assertThatThrownBy(() -> parse(policyXml("timeout=\"PT0.0001S\""))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> parse(policyXml("initialBackoff=\"PT0.0015S\""))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> parse(policyXml("timeout=\"-PT0.0001S\""))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsAttemptTimeoutLongerThanTheInvocationTimeout() {
        assertThatThrownBy(() -> parse(policyXml("timeout=\"PT5S\" attemptTimeout=\"PT20S\"")))
            .isInstanceOf(com.alibaba.compileflow.engine.CompileFlowException.class)
            .hasMessageContaining("attemptTimeoutMs must be less than or equal to timeoutMs");
    }

    @Test
    void rejectsDuplicatePoliciesWhenSchemaValidationIsDisabled() {
        String xml = policyXml("maxAttempts=\"1\"")
            .replace("<invocationPolicy maxAttempts=\"1\"/>",
                    "<invocationPolicy maxAttempts=\"1\"/><invocationPolicy maxAttempts=\"2\"/>");

        assertThatThrownBy(() -> TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("test.invocation-policy", xml.getBytes(StandardCharsets.UTF_8)),
                    com.alibaba.compileflow.engine.core.xml.parser.SchemaValidation.DISABLED))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void preservesAnExplicitEmptyPolicyWhenWriting() {
        TbbpmModel model = parse(policyXml(""));

        ByteArrayOutputStream output = (ByteArrayOutputStream) TbbpmXmlWriter.getInstance().write(model);
        String written = output.toString(StandardCharsets.UTF_8);

        assertThat(written.contains("<invocationPolicy")).isTrue();
        assertThat(((AutoTaskNode) parse(written).getNode("task")).getAction().getInvocationPolicy()).isNotNull();
    }

    @Test
    void rejectsInvocationPolicyOnAnEffectAction() {
        TbbpmModel model = parse(policyXml("maxAttempts=\"1\"", "effect"));

        assertThatThrownBy(() -> new TbbpmSemanticFrontend().compile(model))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("effect")
            .hasMessageContaining("invocationPolicy");
    }
}
