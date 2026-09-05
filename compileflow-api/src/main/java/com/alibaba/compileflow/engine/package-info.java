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
 * Stable entry points and immutable value types for compiling, validating, and executing process
 * definitions.
 *
 * <p>Applications should begin with {@link com.alibaba.compileflow.engine.ProcessEngineFactory}
 * and retain the resulting {@link com.alibaba.compileflow.engine.ProcessEngine} for the lifetime
 * of its configuration boundary.
 *
 * @author yusu
 */
package com.alibaba.compileflow.engine;
