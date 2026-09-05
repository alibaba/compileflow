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

import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;
import com.alibaba.compileflow.engine.tbbpm.model.TimerTaskNode;
import javax.xml.stream.XMLStreamWriter;

/**
 * Canonical XML writer for the TBBPM timer suspension task.
 *
 * @author yusu
 */
public class TimerTaskWriter extends AbstractTbbpmNodeWriter<TimerTaskNode> {
    @Override
    protected String getName() {
        return TbbpmModelConstants.TIMER_TASK;
    }

    @Override
    protected void enrichNodeAttr(TimerTaskNode node, XMLStreamWriter writer) throws Exception {
        writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_DURATION, node.getDuration());
        writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_DURATION_EXPRESSION, node.getDurationExpression());
        writeAttribute(writer, TbbpmModelConstants.ATTRIBUTE_WAKE_AT_EXPRESSION, node.getWakeAtExpression());
    }

    @Override
    public Class<TimerTaskNode> getElementClass() {
        return TimerTaskNode.class;
    }
}
