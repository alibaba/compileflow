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
 * Represents the BPMN 2.0 'partnerEntity' element.
 * <p>
 * A Partner Entity represents an external entity that participates in a collaboration
 * but is not under the control of the process owner. It references one or more
 * participants.
 *
 * @author yusu
 */
public class PartnerEntity extends RootElement {

    /**
     * A list of references to the Participants that this Partner Entity represents.
     */
    private List<String> participantRef;

    /**
     * The name of the Partner Entity.
     */
    private String name;

}
