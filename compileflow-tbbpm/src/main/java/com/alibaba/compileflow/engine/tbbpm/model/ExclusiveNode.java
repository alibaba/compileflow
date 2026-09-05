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

import com.alibaba.compileflow.engine.core.model.ExclusiveGatewayElement;

/**
 * Routes execution along exactly one outgoing transition.
 *
 * <p>Evaluates transition conditions in declaration order and follows the first
 * matching path. If no condition matches, follows the default transition
 * (the one without a condition). A converging exclusive gateway is an
 * exclusive merge and does not synchronize tokens.
 *
 * @author wuxiang
 * @author yusu
 */
public class ExclusiveNode extends GatewayNode implements ExclusiveGatewayElement<Transition> {
}
