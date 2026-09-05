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

import com.alibaba.compileflow.engine.core.model.ForEachElement;
import java.util.Objects;

/**
 * Structured collection loop.
 */
public final class ForEachNode extends LoopScopeNode implements ForEachElement {
    private String collection;
    private String item;
    private String itemType;
    private String index;
    private Execution execution = Execution.SEQUENTIAL;
    private ForEachOutput output;

    @Override
    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    @Override
    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    @Override
    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    @Override
    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    @Override
    public Execution getExecution() {
        return execution;
    }

    public void setExecution(Execution execution) {
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    public ForEachOutput getOutput() {
        return output;
    }

    public void setOutput(ForEachOutput output) {
        this.output = output;
    }

    @Override
    public String getOutputTarget() {
        return output == null ? null : output.getTarget();
    }

    @Override
    public String getOutputSource() {
        return output == null ? null : output.getSource();
    }

    @Override
    protected String elementName() {
        return TbbpmModelConstants.FOREACH;
    }
}
