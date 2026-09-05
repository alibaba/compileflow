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
package com.alibaba.compileflow.engine.tbbpm;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.core.source.AbstractFlowModelReader;
import com.alibaba.compileflow.engine.core.xml.parser.FlowSource;
import com.alibaba.compileflow.engine.tbbpm.parser.TbbpmXmlParser;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModel;

/**
 * Reads {@code .bpm} XML streams into {@link TbbpmModel} object graphs.
 *
 * @author yusu
 * @see TbbpmXmlParser
 */
public final class TbbpmModelReader extends AbstractFlowModelReader<TbbpmModel> {
    @Override
    protected ProcessModelType getFlowModelType() {
        return ProcessModelType.TBBPM;
    }

    @Override
    protected TbbpmModel read(FlowSource source) {
        return TbbpmXmlParser.getInstance().parse(source);
    }
}
