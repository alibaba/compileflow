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

import java.util.List;

/**
 * Represents the BPMN 2.0 'choreographyTask' element.
 * <p>
 * A Choreography Task represents an interaction between two Participants. It is a
 * self-contained unit of work that involves the exchange of one or more messages.
 *
 * @author yusu
 */
public class ChoreographyTask extends ChoreographyActivity {

    /**
     * A list of references to the Message Flows that are associated with this
     * Choreography Task. A Choreography Task must have at least one and at most
     * two Message Flow references.
     */
    private List<String> messageFlowRef;

}
