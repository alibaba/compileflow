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
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessTrigger;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TriggerEntryContractTest {
    private static final ProcessEngineConfig CONFIG = ProcessEngineConfig
        .tbbpmBuilder()
        .discoverPlugins(false)
        .build();

    private static String singleWaitFlow(String code, String nodeId) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="%s" name="Trigger Entry">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="%s"/>
                </start>
                <waitEventTask id="%s" name="Wait"
                               event="approved&quot;event" g="80,0,100,40">
                    <transition to="end"/>
                </waitEventTask>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """
            .formatted(code, nodeId, nodeId);
    }

    private static String incompleteForeachFlow(String code) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="%s" name="Invalid For Each">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="loop"/>
                </start>
                <foreach id="loop" name="Loop" g="80,0,100,40"
                             item="item" itemType="java.lang.Object">
                    <transition to="end"/>
                    <start id="bodyStart"><transition to="body"/></start>
                    <autoTask id="body" name="Body" g="100,20,100,40"><transition to="bodyEnd"/></autoTask>
                    <end id="bodyEnd"/>
                </foreach>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """
            .formatted(code);
    }

    private static String duplicateNestedNodeIdFlow(String code) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="%s" name="Duplicate Nested Node Id">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="shared"/>
                </start>
                <autoTask id="shared" name="Top Level" g="50,0,100,40">
                    <transition to="loop"/>
                </autoTask>
                <while id="loop" name="Loop" g="180,0,100,40"
                           condition="false" maxIterations="100">
                    <transition to="end"/>
                    <start id="bodyStart"><transition to="shared"/></start>
                    <autoTask id="shared" name="Loop Body" g="200,20,100,40"><transition to="bodyEnd"/></autoTask>
                    <end id="bodyEnd"/>
                </while>
                <end id="end" name="End" g="320,0,32,32"/>
            </bpm>
            """
            .formatted(code);
    }

    private static String noteTargetFlow(String code) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="%s" name="Invalid Note Target">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="note"/>
                </start>
                <note id="note" name="Note" comment="Diagram only" g="80,0,100,40"/>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """
            .formatted(code);
    }

    private static String unreachableNodeFlow(String code) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpm code="%s" name="Unreachable Node">
                <start id="start" name="Start" g="0,0,32,32">
                    <transition to="end"/>
                </start>
                <autoTask id="orphan" name="Orphan" g="80,80,100,40">
                    <transition to="end"/>
                </autoTask>
                <end id="end" name="End" g="220,0,32,32"/>
            </bpm>
            """
            .formatted(code);
    }

    @Test
    void usesTriggerEntryIdsForExternalEntriesAndInternalControlFlow() {
        String code = "test.stateful.trigger.entry";
        ProcessDefinition definition = ProcessDefinition.inline(code, singleWaitFlow(code, "approval&quot;entry"));
        ProcessRef.Version ref = ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, code, "v1");

        try (ProcessEngine engine = ProcessEngineFactory.create(CONFIG)) {
            String javaCode = engine.tooling().generateJavaCode(definition);

            assertThat(javaCode)
                .contains("switch (nodeId) {", "case \"approval\\\"entry\" -> _cf$runApprovalEntry();",
                        "TriggerValidation.requireEvent(\"approval\\\"entry\", " + "\"approved\\\"event\", this._cf$triggerEvent);")
                .doesNotContain("_cf$run(nodeId, null);", "case \"start\"", "case \"end\"", "case \"null\"");

            engine.runtime().load(ref, definition);
            ProcessResult<Map<String, Object>> result =
                    engine.trigger(ref, ProcessTrigger.on("approval\"entry", "approved\"event"), Map.of());

            assertThat(result.isSuccess())
                .withFailMessage(() -> result.getError() == null
                        ? "trigger failed without a ProcessError"
                        : result.getError().getCode() + ": " + result.getError().getMessage())
                .isTrue();
            assertThat(result.getOutput()).isEqualTo(Map.of());
        }
    }

    @Test
    void fastPreflightRejectsRemovedTagAttribute() {
        String code = "test.stateful.trigger.removed-tag";
        ProcessDefinition definition = ProcessDefinition.inline(code,
                singleWaitFlow(code, "wait").replace("name=\"Wait\"", "name=\"Wait\" tag=\"legacy\""));

        try (ProcessEngine engine = ProcessEngineFactory.create(CONFIG)) {
            ProcessPreflightReport report = engine.tooling().preflight(definition, ProcessPreflightOptions.fast());

            assertThat(report.getOverallStatus()).isEqualTo(ProcessPreflightReport.OverallStatus.FAIL);
            assertThat(report.getItems().size()).isEqualTo(1);
            assertThat(report.getItems().get(0).getStatus()).isEqualTo(ProcessPreflightReport.ItemStatus.FAIL);
            assertThat(report.getItems().get(0).getMessage().contains("tag")).isTrue();
        }
    }

    @Test
    void fastPreflightRejectsIncompleteLoopSemantics() {
        String code = "test.loop.invalid.foreach";
        ProcessDefinition definition = ProcessDefinition.inline(code, incompleteForeachFlow(code));

        try (ProcessEngine engine = ProcessEngineFactory.create(CONFIG)) {
            ProcessPreflightReport report = engine.tooling().preflight(definition, ProcessPreflightOptions.fast());

            assertThat(report.getOverallStatus()).isEqualTo(ProcessPreflightReport.OverallStatus.FAIL);
            assertThat(report.getItems().get(0).getMessage().contains("collection")).isTrue();
        }
    }

    @Test
    void fastPreflightRejectsNestedNodeIdsThatCollideWithTopLevelNodes() {
        String code = "test.loop.duplicate.nested.id";
        ProcessDefinition definition = ProcessDefinition.inline(code, duplicateNestedNodeIdFlow(code));

        try (ProcessEngine engine = ProcessEngineFactory.create(CONFIG)) {
            ProcessPreflightReport report = engine.tooling().preflight(definition, ProcessPreflightOptions.fast());

            assertThat(report.getOverallStatus()).isEqualTo(ProcessPreflightReport.OverallStatus.FAIL);
            ProcessPreflightReport.Item item = report.getItems().get(0);
            assertThat(item.getType()).isEqualTo(ProcessPreflightReport.ItemType.LINT);
            assertThat(item.getStatus()).isEqualTo(ProcessPreflightReport.ItemStatus.FAIL);
            assertThat(item.getMessage().contains("duplicate node id across the complete model")).isTrue();
            assertThat(item.getMessage().contains("shared")).isTrue();
        }
    }

    @Test
    void fastPreflightRejectsTransitionsToDiagramOnlyNotes() {
        String code = "test.note.transition.target";
        ProcessDefinition definition = ProcessDefinition.inline(code, noteTargetFlow(code));

        try (ProcessEngine engine = ProcessEngineFactory.create(CONFIG)) {
            ProcessPreflightReport report = engine.tooling().preflight(definition, ProcessPreflightOptions.fast());

            assertThat(report.getOverallStatus()).isEqualTo(ProcessPreflightReport.OverallStatus.FAIL);
            assertThat(report.getItems().get(0).getStatus()).isEqualTo(ProcessPreflightReport.ItemStatus.FAIL);
        }
    }

    @Test
    void fastPreflightRejectsAnEmbeddedProcessCodeThatDiffersFromItsDefinition() {
        String code = "test.identity.expected";
        ProcessDefinition definition = ProcessDefinition.inline(code, singleWaitFlow("test.identity.other", "approval"));

        try (ProcessEngine engine = ProcessEngineFactory.create(CONFIG)) {
            ProcessPreflightReport report = engine.tooling().preflight(definition, ProcessPreflightOptions.fast());

            assertThat(report.getOverallStatus()).isEqualTo(ProcessPreflightReport.OverallStatus.FAIL);
            assertThat(report.getItems().get(0).getMessage().contains("Process definition code does not match")).isTrue();
        }
    }

    @Test
    void fastPreflightRejectsUnreachableExecutableNodes() {
        String code = "test.unreachable";
        ProcessDefinition definition = ProcessDefinition.inline(code, unreachableNodeFlow(code));

        try (ProcessEngine engine = ProcessEngineFactory.create(CONFIG)) {
            ProcessPreflightReport report = engine.tooling().preflight(definition, ProcessPreflightOptions.fast());

            assertThat(report.getOverallStatus()).isEqualTo(ProcessPreflightReport.OverallStatus.FAIL);
            assertThat(report.getItems().get(0).getMessage().contains("unreachable from its start node")).isTrue();
            assertThat(report.getItems().get(0).getMessage().contains("orphan")).isTrue();
        }
    }
}
