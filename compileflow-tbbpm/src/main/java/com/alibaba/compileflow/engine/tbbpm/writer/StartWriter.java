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

import com.alibaba.compileflow.engine.tbbpm.model.StartNode;
import com.alibaba.compileflow.engine.tbbpm.model.TbbpmModelConstants;

/**
 * XML writer for TBBPM start nodes.
 *
 * @author yusu
 */
public class StartWriter extends AbstractTbbpmNodeWriter<StartNode> {
    @Override
    protected String getName() {
        return TbbpmModelConstants.START;
    }

    @Override
    public Class<StartNode> getElementClass() {
        return StartNode.class;
    }
}
