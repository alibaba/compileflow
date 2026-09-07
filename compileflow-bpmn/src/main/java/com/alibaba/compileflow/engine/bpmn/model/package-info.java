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
/**
 * BPMN 2.0 process definition model.
 *
 * <p>This package contains the executable subset of BPMN 2.0 elements that
 * CompileFlow supports. Executable elements are parsed through
 * {@code BpmnElementParserRegistry}, normalized by
 * {@code BpmnSemanticFrontend}, and realized by the shared runtime backends.
 * Definitions that use unsupported node types fail {@code preflight} with a
 * clear error.
 *
 * <h2>Executable subset</h2>
 *
 * <ul>
 *   <li>{@link StartEvent}, {@link EndEvent}</li>
 *   <li>{@link ServiceTask}, {@link ScriptTask}, {@link ReceiveTask}</li>
 *   <li>{@link IntermediateCatchEvent} with message or timer definitions</li>
 *   <li>{@link ExclusiveGateway}, {@link ParallelGateway}, {@link InclusiveGateway}</li>
 *   <li>{@link SubProcess}, {@link CallActivity}</li>
 *   <li>{@link MultiInstanceLoopCharacteristics}, {@link StandardLoopCharacteristics}</li>
 *   <li>{@link Message}, {@link SequenceFlow}</li>
 * </ul>
 *
 * <p>See {@code docs/en/node-support.md} for the user-facing supported subset
 * and {@code docs/en/architecture/process-model.md} for the BPMN 2.0 protocol
 * specification.
 *
 * <h2>Unsupported elements</h2>
 *
 * <p>Elements that CompileFlow cannot execute (userTask, manualTask,
 * businessRuleTask, sendTask, boundaryEvent, unsupported intermediate events, signal,
 * choreography, collaboration, conversation, global task family, data
 * stores, resource roles, etc.) are outside this package's executable subset. They
 * require semantics that are not defined by the current shared Process profile. Unsupported
 * collaboration, compensation, and event constructs are rejected because their Process
 * meaning and recovery contract have not been proven, not because an implementation lacks
 * persistence infrastructure; the optional Durable product defines a separate recovery profile.
 *
 * <p>Unknown or unsupported elements fail parsing because the parser registry has
 * no implementation for them. The only ignored elements are the standard
 * {@code incoming} and {@code outgoing} reference text nodes; executable
 * transitions are built from {@link SequenceFlow} definitions.
 *
 * <h2>Stability</h2>
 * <p>
 * Classes in this package are an internal implementation detail of the
 * {@code compileflow-bpmn} module. They are not part of the public
 * CompileFlow API surface (see {@code docs/en/architecture/supported-surfaces.md}).
 *
 * @author yusu
 */
package com.alibaba.compileflow.engine.bpmn.model;
