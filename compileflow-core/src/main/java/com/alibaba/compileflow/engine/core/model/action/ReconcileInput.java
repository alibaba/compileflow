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
package com.alibaba.compileflow.engine.core.model.action;

import com.alibaba.compileflow.engine.core.model.AbstractElement;
import com.alibaba.compileflow.engine.core.type.DataTypes;

/**
 * Maps one persisted Effect request field to a reconcile invocation argument.
 *
 * @author yusu
 */
public final class ReconcileInput extends AbstractElement {
    private String source;
    private String target;
    private String dataType;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getDataType() {
        return DataTypes.normalizeToObjectTypeName(dataType);
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }
}
