/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.bpmn.definition;

import com.alibaba.compileflow.engine.core.definition.BaseFlowElement;

import java.util.List;

/**
 * Represents the BPMN 2.0 'subChoreography' element.
 * <p>
 * A sub-choreography is a compound activity that contains a refined choreography,
 * allowing for the decomposition of complex choreographies into smaller, reusable parts.
 * It encapsulates a set of flow elements and artifacts that define its internal logic.
 *
 * @author yusu
 */
public class SubChoreography extends ChoreographyActivity {

    /**
     * The sequence of flow elements (e.g., tasks, gateways, events) that make up
     * the internal logic of this sub-choreography.
     */
    private List<BaseFlowElement> flowElements;

    /**
     * The artifacts (e.g., annotations, associations) that are part of this sub-choreography.
     */
    private List<Artifact> artifacts;

}
