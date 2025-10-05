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
package com.alibaba.compileflow.engine.core.definition;

/**
 * Represents an exclusive (XOR) gateway, a decision point in a process.
 * <p>
 * An exclusive gateway routes the flow to exactly one of its outgoing transitions.
 * The first transition whose condition evaluates to {@code true} is taken. The
 * evaluation order is determined by the {@link Transition#getPriority()}.
 * It is recommended to provide a default transition (without a condition or with a
 * condition that is always true) with the lowest priority to handle cases where
 * no other conditions are met.
 *
 * @author yusu
 */
public interface ExclusiveGatewayElement<T extends Transition> extends TransitionNode<T> {

}
