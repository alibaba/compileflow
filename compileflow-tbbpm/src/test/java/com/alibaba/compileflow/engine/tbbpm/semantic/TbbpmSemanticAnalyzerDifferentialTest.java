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
package com.alibaba.compileflow.engine.tbbpm.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.core.controlflow.GatewayPlan;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowPlan;
import com.alibaba.compileflow.engine.core.controlflow.StructuredControlFlowAnalyzer;
import com.alibaba.compileflow.engine.core.semantic.plan.ProcessSemanticPlan;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TbbpmSemanticAnalyzerDifferentialTest {
    @Test
    void semanticAnalyzerMatchesTheFrozenStructuredParallelContract() {
        TbbpmModel model = TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("semantic.analyzer.differential", FLOW.getBytes(StandardCharsets.UTF_8)));
        StructuredControlFlowAnalyzer analyzer = new StructuredControlFlowAnalyzer();

        StructuredControlFlowPlan semantic = analyzer.analyze(new TbbpmSemanticFrontend().compile(model));
        GatewayPlan split = semantic.requireGatewayPlan("split");

        assertThat(semantic.getGatewayPlans()).containsOnlyKeys("split", "join");
        assertThat(split.getConvergenceNodeId()).isEqualTo("join");
        assertThat(split.getBranches().values())
            .extracting(branch -> branch.getNodeIds())
            .containsExactly(java.util.List.of("left"), java.util.List.of("right"));
        assertThat(split.getContinuationNodeIds()).containsExactly("after");
        assertThat(split.isConcurrent()).isTrue();
        assertThat(split.isSuspending()).isFalse();
        assertThat(semantic.requireGatewayPlan("join").isJoin()).isTrue();
    }

    @Test
    void semanticAnalyzerRetainsConfiguredLoopBoundaryWhenTheEndIsBreak() {
        TbbpmModel model = parse(LOOP_WITH_BREAK_BOUNDARY);
        StructuredControlFlowAnalyzer analyzer = new StructuredControlFlowAnalyzer();

        ProcessSemanticPlan semantics = new TbbpmSemanticFrontend().compile(model);
        StructuredControlFlowPlan structured = analyzer.analyze(semantics);
        GatewayPlan fork = structured.requireGatewayPlan("fork");

        assertThat(semantics.requireNode("loop").scopeBoundary())
            .isEqualTo(new ProcessSemanticPlan.ScopeBoundary("loopStart", "loopEnd"));
        assertThat(structured.getGatewayPlans()).containsOnlyKeys("fork", "join");
        assertThat(fork.getConvergenceNodeId()).isEqualTo("join");
        assertThat(fork.getBranches().values())
            .extracting(branch -> branch.getNodeIds())
            .containsExactly(java.util.List.of("left"), java.util.List.of("right"));
        assertThat(fork.getContinuationNodeIds()).containsExactly("stop");
        assertThat(structured.requireGatewayPlan("join").isJoin()).isTrue();
    }

    private static TbbpmModel parse(String source) {
        return TbbpmXmlParser
            .getInstance()
            .parse(FlowSource.of("semantic.analyzer.differential", source.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String FLOW =
            """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpm code="semantic.analyzer.differential" name="Semantic Analyzer Differential">
            <start id="start" name="Start" g="0,0,32,32">
                <transition to="split"/>
            </start>
            <parallel id="split" name="Split" g="60,0,48,48">
                <transition to="left"/>
                <transition to="right"/>
            </parallel>
            <autoTask id="left" name="Left" g="140,0,100,40">
                <transition to="join"/>
            </autoTask>
            <autoTask id="right" name="Right" g="140,80,100,40">
                <transition to="join"/>
            </autoTask>
            <parallel id="join" name="Join" g="280,40,48,48">
                <transition to="after"/>
            </parallel>
            <autoTask id="after" name="After" g="360,40,100,40">
                <transition to="end"/>
            </autoTask>
            <end id="end" name="End" g="500,40,32,32"/>
        </bpm>
        """;
    private static final String LOOP_WITH_BREAK_BOUNDARY =
            """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpm code="semantic.analyzer.loop-boundary" name="Loop Boundary Differential">
            <start id="start" name="Start" g="0,0,32,32">
                <transition to="loop"/>
            </start>
            <while id="loop" name="Loop" g="60,0,360,200"
                       condition="true" maxIterations="10">
                <transition to="end"/>
                <start id="loopStart"><transition to="fork"/></start>
                <parallel id="fork" name="Fork" g="90,40,48,48">
                    <transition to="left"/>
                    <transition to="right"/>
                </parallel>
                <autoTask id="left" name="Left" g="170,0,100,40">
                    <transition to="join"/>
                </autoTask>
                <autoTask id="right" name="Right" g="170,100,100,40">
                    <transition to="join"/>
                </autoTask>
                <parallel id="join" name="Join" g="300,40,48,48">
                    <transition to="stop"/>
                </parallel>
                <break id="stop" name="Break" g="380,40,32,32"/>
                <end id="loopEnd"/>
            </while>
            <end id="end" name="End" g="470,40,32,32"/>
        </bpm>
        """;
}
