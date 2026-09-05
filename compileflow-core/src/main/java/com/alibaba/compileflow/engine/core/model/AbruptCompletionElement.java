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

/**
 * Marks a node that can end its current structured container without
 * following a normal outgoing transition.
 *
 * <p>Examples are generated {@code break} and {@code continue} statements.
 * Every abrupt node is analyzed with a synthetic edge to the container
 * boundary. A guarded abrupt node may also have one normal outgoing edge for
 * the guard-false path; the synthetic edge is analysis-only and is never
 * emitted into generated code.
 *
 * @author yusu
 */
public interface AbruptCompletionElement extends Node {
}
