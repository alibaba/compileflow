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
 * PostgreSQL implementation of the CompileFlow Durable Store.
 *
 * <p>The Store persists fixed Kernel envelopes. Disk, backup and transport
 * encryption are PostgreSQL/deployment responsibilities and do not alter the
 * persisted Process identity or Kernel protocol.</p>
 *
 * @author yusu
 */
package com.alibaba.compileflow.durable.postgres;
