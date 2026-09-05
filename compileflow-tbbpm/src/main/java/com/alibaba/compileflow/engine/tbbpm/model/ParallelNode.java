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

import com.alibaba.compileflow.engine.core.model.ParallelGatewayElement;

/**
 * Forks execution into all outgoing transitions or joins a matching fork.
 *
 * <p>A split has one incoming and multiple outgoing transitions. A join has
 * multiple incoming and one outgoing transition; execution continues only
 * after every branch completes.
 *
 * @author yusu
 */
public class ParallelNode extends GatewayNode implements ParallelGatewayElement<Transition> {
}
