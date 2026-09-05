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
package com.alibaba.compileflow.durable.runtime.state;

import com.alibaba.compileflow.durable.api.validation.DurableIdentifiers;

/**
 * Process-owned type and admission authority for one declared state variable.
 *
 * @author yusu
 */
public record ProcessStateField(String name, String declaredType, boolean nullable, boolean startInput) {
    public ProcessStateField {
        name = DurableIdentifiers.requireIdentity(name, "name", 256);
        declaredType = DurableIdentifiers.requireIdentity(declaredType, "declaredType", 2_048);
    }
}
