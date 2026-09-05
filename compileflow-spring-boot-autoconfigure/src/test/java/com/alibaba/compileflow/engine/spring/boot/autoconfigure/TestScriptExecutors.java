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
package com.alibaba.compileflow.engine.spring.boot.autoconfigure;

import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

final class TestScriptExecutors {
    private TestScriptExecutors() {
    }

    static ScriptExecutor of(String name, BiFunction<String, Map<String, Object>, Object> evaluator) {
        String language = ScriptExecutor.requireCanonicalName(name);
        BiFunction<String, Map<String, Object>, Object> function = Objects.requireNonNull(evaluator, "evaluator");
        return new ScriptExecutor() {
            @Override
            public String name() {
                return language;
            }

            @Override
            public void validate(ScriptProgramSpec spec) {
                Objects.requireNonNull(spec, "spec");
            }

            @Override
            public ScriptProgram compile(ScriptProgramSpec spec) {
                return new SourceScript(language, Objects.requireNonNull(spec, "spec").source());
            }

            @Override
            public Object evaluate(ScriptProgram script, Map<String, Object> context) {
                SourceScript source = (SourceScript) script;
                return function.apply(source.source(), Objects.requireNonNull(context, "context"));
            }
        };
    }

    private record SourceScript(String language, String source) implements ScriptProgram {}
}
