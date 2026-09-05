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
 * First-party Kernel provider transaction contracts, not application persistence extensions.
 *
 * <p>A Store provider implements the complete contract for its matching Kernel version and must
 * pass the published testkit. New authoritative transitions may require new abstract methods;
 * unsupported capability is not deferred to a default method that fails in production.
 *
 * @author yusu
 */
package com.alibaba.compileflow.durable.spi.store;
