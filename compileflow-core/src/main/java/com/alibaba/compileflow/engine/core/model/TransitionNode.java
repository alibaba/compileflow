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
package com.alibaba.compileflow.engine.core.model;

import java.util.List;

/**
 * Node that participates in incoming and outgoing transitions.
 *
 * @author yusu
 */
public interface TransitionNode<T extends Transition> extends Node {
    List<T> getIncomingTransitions();

    List<T> getOutgoingTransitions();

    List<TransitionNode<?>> getIncomingNodes();

    List<TransitionNode<?>> getOutgoingNodes();
}
