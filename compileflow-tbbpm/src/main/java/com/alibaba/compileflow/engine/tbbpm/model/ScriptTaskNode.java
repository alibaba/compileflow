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

/**
 * Executes workflow-owned code through a registered script language.
 *
 * <p>Class and Spring bean actions belong on {@link AutoTaskNode}; this node is reserved for
 * Script actions with explicit input/output mappings.
 *
 * @author wuxiang
 * @author yusu
 */
public class ScriptTaskNode extends ActivityNode {
}
