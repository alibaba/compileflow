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
package com.alibaba.compileflow.deploy.api.protocol.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifact;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessArtifactPayloadsTest {
    @Test
    void roundTripPreservesExactContentAndExplicitIdentity() throws Exception {
        String content = " \n<definitions/>\n";
        ProcessDefinition.Inline definition = ProcessDefinition.inline("code1", content);
        String digest = ProcessArtifactDigest.compute(ProcessModelType.BPMN, definition, Collections.emptyMap());
        String json =
                ProcessArtifactPayloads.artifactJson("default", "code1", "v1", ProcessModelType.BPMN, content, digest);

        ProcessArtifact parsed = ProcessArtifactParser.parse(json);

        assertThat(parsed.getRef()).isEqualTo(com.alibaba.compileflow.engine.ProcessRef.version("default", "code1", "v1"));
        assertThat(parsed.getModelType()).isEqualTo(ProcessModelType.BPMN);
        assertThat(parsed.getDefinition().content()).isEqualTo(content);
        assertThat(parsed.getArtifactDigest()).isEqualTo(digest);
        assertThat(json).contains("\"schemaVersion\":1");
        assertThat(json).contains("\"artifactDigest\":\"" + digest + "\"");
        assertThat(json).doesNotContain("dependencies", "metadata");
    }

    @Test
    void roundTripPreservesDistinctCallSiteBindingsForTheSameCode() {
        ProcessRef.Version root = ProcessRef.version("shop", "order", "v7");
        ProcessDefinition.Inline definition = ProcessDefinition.inline(root.code(), "<definitions/>");
        List<ProcessCallBinding> bindings = List.of(new ProcessCallBinding("oldPayment",
                        ProcessRef.version("shop", "payment", "v3")),
                new ProcessCallBinding("newPayment", ProcessRef.version("shop", "payment", "v4")));
        Map<String, ProcessRef.Version> targets =
                Map.of("oldPayment", bindings.get(0).target(), "newPayment", bindings.get(1).target());
        String digest = ProcessArtifactDigest.compute(ProcessModelType.BPMN, definition, targets);
        ProcessArtifact artifact = new ProcessArtifact(root, ProcessModelType.BPMN, definition, digest, bindings);

        ProcessArtifact parsed = ProcessArtifactParser.parse(ProcessArtifactPayloads.artifactJson(artifact));

        assertThat(parsed.getCallBindings()).containsOnlyKeys("oldPayment", "newPayment");
        assertThat(parsed.getCallBindings().get("oldPayment").target().version()).isEqualTo("v3");
        assertThat(parsed.getCallBindings().get("newPayment").target().version()).isEqualTo("v4");
        assertThat(ProcessArtifactDigest.compute(ProcessModelType.BPMN, definition,
                Map.of("oldPayment", bindings.get(1).target(), "newPayment", bindings.get(0).target())))
            .isNotEqualTo(digest);
    }

    @Test
    void publicationIdentityDoesNotChangeExecutableDigest() {
        ProcessDefinition.Inline definition = ProcessDefinition.inline("code1", "<definitions/>");
        String digest = ProcessArtifactDigest.compute(ProcessModelType.BPMN, definition, Map.of());

        ProcessArtifact v1 =
                new ProcessArtifact(ProcessRef.version("shop", "code1", "v1"), ProcessModelType.BPMN, definition, digest);
        ProcessArtifact v2 =
                new ProcessArtifact(ProcessRef.version("shop", "code1", "v2"), ProcessModelType.BPMN, definition, digest);

        assertThat(v1.getRef()).isNotEqualTo(v2.getRef());
        assertThat(v1.getArtifactDigest()).isEqualTo(v2.getArtifactDigest());
    }

    @Test
    void parserRejectsMissingOrMismatchedDigest() {
        String missingDigest =
                """
            {"schemaVersion":1,"namespace":"default","code":"code1","version":"v1",
             "modelType":"BPMN","content":"<definitions/>","callBindings":[]}
            """;
        String mismatchedDigest =
                """
            {"schemaVersion":1,"namespace":"default","code":"code1","version":"v1",
             "modelType":"BPMN","content":"<definitions/>",
             "callBindings":[],
             "artifactDigest":"0000000000000000000000000000000000000000000000000000000000000000"}
            """;

        assertThatThrownBy(() -> ProcessArtifactParser.parse(missingDigest))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> ProcessArtifactParser.parse(mismatchedDigest))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH));
    }

    @Test
    void parserRejectsUnknownSchemaFields() {
        String digest = digest("<definitions/>");
        String valid = """
            {"schemaVersion":1,"namespace":"default","code":"code1","version":"v1",
             "modelType":"BPMN","content":"<definitions/>","artifactDigest":"%s","callBindings":[]}
            """
            .formatted(digest);

        assertThatThrownBy(() -> ProcessArtifactParser.parse(valid.replace("\"schemaVersion\":1",
                "\"schemaVersion" + "\":2")))
            .hasMessageContaining("schemaVersion");
        assertThatThrownBy(() -> ProcessArtifactParser.parse(valid.replace("\"schemaVersion\":1",
                "\"schemaVersion\":\"1\"")))
            .hasMessageContaining("must be an integer");
        assertThatThrownBy(() -> ProcessArtifactParser.parse(valid.replace("\"artifactDigest\"",
                "\"unsupported\":true,\"artifactDigest\"")))
            .hasMessageContaining("unsupported field");
        assertThatThrownBy(() -> ProcessArtifactParser.parse(valid.replace("\"artifactDigest\"",
                "\"metadata\":{},\"artifactDigest\"")))
            .hasMessageContaining("unsupported field");
    }

    @Test
    void parserRejectsDuplicateFieldsAndTrailingJson() {
        String digest = digest("<definitions/>");
        String valid = """
            {"schemaVersion":1,"namespace":"default","code":"code1","version":"v1",
             "modelType":"BPMN","content":"<definitions/>","artifactDigest":"%s","callBindings":[]}
            """
            .formatted(digest);

        assertThatThrownBy(() -> ProcessArtifactParser.parse(valid.replace("\"schemaVersion\":1",
                "\"schemaVersion\":1,\"schemaVersion\":1")))
            .hasMessageContaining("invalid");
        assertThatThrownBy(() -> ProcessArtifactParser.parse(valid + "{}")).hasMessageContaining("invalid");
    }

    @Test
    void serializerRejectsContentDigestMismatch() {
        String digest = digest("<other/>");

        assertThatThrownBy(() -> ProcessArtifactPayloads.artifactJson("default", "code1", "v1", ProcessModelType.BPMN,
                "<definitions/>", digest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match");
    }

    @Test
    void parserRejectsInvalidJsonWithTypedDeploymentError() {
        assertThatThrownBy(() -> ProcessArtifactParser.parse("not-json"))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.INVALID_ARGUMENT));
    }

    private static String digest(String content) {
        return ProcessArtifactDigest.compute(ProcessModelType.BPMN, ProcessDefinition.inline("code1", content),
                Collections.emptyMap());
    }
}
