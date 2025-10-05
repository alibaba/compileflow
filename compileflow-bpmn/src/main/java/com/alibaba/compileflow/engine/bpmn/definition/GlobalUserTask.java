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
 * Represents the BPMN 2.0 'globalUserTask' element.
 * <p>
 * A Global User Task is a reusable, top-level definition of a user task that can be
 * called from any process. It defines a template for a task that is to be performed
 * by a human actor.
 *
 * @author yusu
 */
public class GlobalUserTask extends GlobalTask {

    /**
     * A list of renderings associated with the user task, typically defining forms or UI
     * hints for clients to display to the user.
     */
    private List<Rendering> rendering;

    /**
     * Specifies the implementation technology, such as a web service or an unspecified
     * mechanism. For user tasks, this is often "##unspecified".
     */
    private String implementation;

}
