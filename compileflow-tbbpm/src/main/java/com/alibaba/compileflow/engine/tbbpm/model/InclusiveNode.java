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

import com.alibaba.compileflow.engine.core.model.InclusiveGatewayElement;

/**
 * Splits into every matching branch or joins the branches selected by a
 * corresponding inclusive split.
 *
 * <p>Unlike {@link ExclusiveNode} (XOR), all matching transitions fire concurrently.
 * If no expressions match, the default (unguarded) transition is taken.
 *
 * @author yusu
 */
public class InclusiveNode extends GatewayNode implements InclusiveGatewayElement<Transition> {
}
