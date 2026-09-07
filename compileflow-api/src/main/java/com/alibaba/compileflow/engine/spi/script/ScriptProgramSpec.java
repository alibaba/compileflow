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
package com.alibaba.compileflow.engine.spi.script;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessText;
import java.util.List;
import java.util.Objects;

/**
 * Immutable specification of one definition-owned script program.
 *
 * <p>The specification belongs to runtime compilation, not to persisted process state. Providers
 * must treat every field as part of program identity: the same source with a different input
 * signature describes a different program.
 *
 * @param language canonical script language identifier
 * @param source definition-owned script source
 * @param inputs immutable declared input signature
 * @param expectedOutputType optional declared output type
 * @author yusu
 */
public record ScriptProgramSpec(String language, String source, List<Input> inputs, String expectedOutputType) {
    public ScriptProgramSpec {
        language = ScriptExecutor.requireCanonicalName(language);
        source = ProcessText.requireNonBlank(source, "source");
        inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
        expectedOutputType = optionalIdentity(expectedOutputType, "expectedOutputType");
    }

    /**
     * One explicit script input available during evaluation.
     *
     * @param name input name
     * @param declaredType declared input type
     */
    public record Input(String name, String declaredType) {
        public Input {
            name = required(name, "name");
            declaredType = required(declaredType, "declaredType");
        }
    }

    private static String required(String value, String name) {
        return ProcessIdentifiers.requireExactIdentity(value, name, Integer.MAX_VALUE);
    }

    private static String optionalIdentity(String value, String name) {
        return value == null ? null : required(value, name);
    }
}
