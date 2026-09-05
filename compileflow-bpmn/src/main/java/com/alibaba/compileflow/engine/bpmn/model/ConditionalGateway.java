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
package com.alibaba.compileflow.engine.bpmn.model;

import org.apache.commons.lang3.StringUtils;

/**
 * Base class for BPMN gateways that route by conditions and may declare a
 * default sequence flow.
 *
 * @author yusu
 */
public abstract class ConditionalGateway extends Gateway {
    private String defaultFlowId;

    @Override
    public SequenceFlow getDefaultTransition() {
        if (StringUtils.isBlank(defaultFlowId)) {
            return null;
        }
        return getOutgoingTransitions()
            .stream()
            .filter(transition -> defaultFlowId.equals(transition.getId()))
            .findFirst()
            .orElse(null);
    }

    public String getDefaultFlowId() {
        return defaultFlowId;
    }

    public void setDefaultFlowId(String defaultFlowId) {
        this.defaultFlowId = defaultFlowId;
    }
}
