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

/**
 * Declarative BPMN gateway direction.
 *
 * <p>Control-flow topology remains authoritative. The declaration is retained
 * for BPMN interoperability and validated against the connected graph.
 *
 * @author yusu
 */
public enum GatewayDirection {
    UNSPECIFIED("Unspecified"),
    CONVERGING("Converging"),
    DIVERGING("Diverging"),
    MIXED("Mixed");
    private final String xmlValue;

    GatewayDirection(String xmlValue) {
        this.xmlValue = xmlValue;
    }

    public String getXmlValue() {
        return xmlValue;
    }
}
