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
package com.alibaba.compileflow.engine.tbbpm.model;

import com.alibaba.compileflow.engine.core.model.AbstractFlowModel;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory object representation of a TBBPM process definition.
 *
 * <p>Root of the object graph created by
 * {@link com.alibaba.compileflow.engine.tbbpm.TbbpmModelReader}
 * when parsing a {@code .bpm} file. Holds all nodes, transitions, and metadata.
 *
 * @author wuxiang
 * @author yusu
 */
public class TbbpmModel extends AbstractFlowModel<FlowNode> implements TbbpmNodeContainer {
    /**
     * A human-readable description of the process.
     */
    private String description;
    /**
     * A list containing every node defined in the process.
     */
    private List<FlowNode> allNodes = new ArrayList<>();

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public List<FlowNode> getAllNodes() {
        return allNodes;
    }

    public void setAllNodes(List<FlowNode> allNodes) {
        this.allNodes = allNodes;
    }

    @Override
    public void addNode(FlowNode node) {
        allNodes.add(node);
    }
}
