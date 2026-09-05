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
 * Deterministic alias and version routing extension contracts.
 *
 * <p>Routers select only from the versions supplied by the engine. Routing attributes are
 * request-scoped inputs and must not be copied into results, events, logs, or process variables.
 *
 * @author yusu
 */
package com.alibaba.compileflow.engine.spi.routing;
