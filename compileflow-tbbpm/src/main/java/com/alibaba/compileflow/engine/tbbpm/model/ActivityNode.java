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

import com.alibaba.compileflow.engine.core.model.action.Action;
import com.alibaba.compileflow.engine.core.model.action.HasAction;

/**
 * Abstract base class for single-action TBBPM activity nodes.
 *
 * <p>Activity nodes carry exactly one {@link com.alibaba.compileflow.engine.core.model.action.Action}
 * and complete synchronously before passing control to the next node.
 * Concrete subtypes include {@link AutoTaskNode} and {@link ScriptTaskNode}.
 *
 * @author yusu
 */
public abstract class ActivityNode extends FlowNode implements HasAction {
    private Action action;

    @Override
    public Action getAction() {
        return action;
    }

    @Override
    public void setAction(Action action) {
        this.action = action;
    }
}
