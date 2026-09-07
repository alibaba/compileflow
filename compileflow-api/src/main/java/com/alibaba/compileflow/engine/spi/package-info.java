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
 * Application extension contracts and the version-coupled engine bootstrap contract.
 *
 * <p>The engine bootstrap implementation is discovered with {@link java.util.ServiceLoader}; application plugins are
 * assembled into immutable engine configuration snapshots. Implementations must document their
 * thread-safety and lifecycle expectations.
 *
 * @author yusu
 */
package com.alibaba.compileflow.engine.spi;
