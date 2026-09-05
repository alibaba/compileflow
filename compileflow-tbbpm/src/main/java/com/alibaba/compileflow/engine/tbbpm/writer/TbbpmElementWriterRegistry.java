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
package com.alibaba.compileflow.engine.tbbpm.writer;

import com.alibaba.compileflow.engine.core.xml.writer.AbstractFlowElementWriterRegistry;
import java.util.List;

/**
 * Registry of TBBPM flow element XML writers.
 *
 * @author yusu
 */
final class TbbpmElementWriterRegistry extends AbstractFlowElementWriterRegistry {
    private static final TbbpmElementWriterRegistry INSTANCE = new TbbpmElementWriterRegistry();

    private TbbpmElementWriterRegistry() {
        super(List.of(new StartWriter(), new EndWriter(), new AutoTaskWriter(), new ScriptTaskWriter(),
                new WaitTaskWriter(), new WaitEventTaskWriter(), new TimerTaskWriter(), new ExclusiveWriter(),
                new ParallelWriter(), new InclusiveWriter(), new WhileWriter(), new ForEachWriter(), new SubBpmWriter(),
                new BpmCallWriter(), new ContinueWriter(), new BreakWriter(), new NoteWriter()));
    }

    static TbbpmElementWriterRegistry getInstance() {
        return INSTANCE;
    }
}
