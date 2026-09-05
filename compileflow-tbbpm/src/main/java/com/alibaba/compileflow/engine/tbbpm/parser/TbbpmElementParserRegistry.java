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

import com.alibaba.compileflow.engine.core.xml.parser.AbstractFlowElementParserRegistry;
import com.alibaba.compileflow.engine.tbbpm.parser.action.ActionParser;
import com.alibaba.compileflow.engine.tbbpm.parser.action.ReconcileActionParser;
import com.alibaba.compileflow.engine.tbbpm.parser.action.ScriptSourceParser;
import com.alibaba.compileflow.engine.tbbpm.parser.execution.InvocationPolicyParser;
import com.alibaba.compileflow.engine.tbbpm.parser.execution.EffectPolicyParser;
import com.alibaba.compileflow.engine.tbbpm.parser.var.VarParser;
import com.alibaba.compileflow.engine.tbbpm.parser.var.InputParser;
import com.alibaba.compileflow.engine.tbbpm.parser.var.OutputParser;
import java.util.List;

/**
 * Provider for TBBPM flow element XML parsers.
 *
 * @author wuxiang
 * @author yusu
 */
final class TbbpmElementParserRegistry extends AbstractFlowElementParserRegistry {
    private static final TbbpmElementParserRegistry INSTANCE = new TbbpmElementParserRegistry();

    private TbbpmElementParserRegistry() {
        super(List.of(new TbbpmDocumentParser(), new StartParser(), new EndParser(), new AutoTaskParser(),
                new ScriptTaskParser(), new ExclusiveParser(), new SubBpmParser(), new BpmCallParser(),
                new WaitTaskParser(), new WaitEventTaskParser(), new TimerTaskParser(), new WhileParser(),
                new ForEachParser(), new ContinueParser(), new BreakParser(), new NoteParser(), new TransitionParser(),
                new ParallelParser(), new InclusiveParser(), new VarParser(), new InputParser(), new OutputParser(),
                new InvocationPolicyParser(), new EffectPolicyParser(), new ActionParser(), new ReconcileActionParser(),
                new ScriptSourceParser()));
    }

    static TbbpmElementParserRegistry getInstance() {
        return INSTANCE;
    }
}
