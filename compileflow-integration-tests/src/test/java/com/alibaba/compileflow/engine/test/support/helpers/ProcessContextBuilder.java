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
package com.alibaba.compileflow.engine.test.support.helpers;

import java.util.HashMap;
import java.util.Map;

public class ProcessContextBuilder {
    private final Map<String, Object> context;

    private ProcessContextBuilder() {
        this.context = new HashMap<>();
    }

    public static ProcessContextBuilder newContext() {
        return new ProcessContextBuilder();
    }

    public ProcessContextBuilder withCalculation(int a, int b) {
        context.put("a", a);
        context.put("b", b);
        return this;
    }

    public ProcessContextBuilder withInlineCalculation(int a, int b) {
        context.put("inputA", a);
        context.put("inputB", b);
        return this;
    }

    public ProcessContextBuilder withOperation(String op) {
        context.put("op", op);
        return this;
    }

    public ProcessContextBuilder withFlag(boolean flag) {
        context.put("flag", flag);
        return this;
    }

    public ProcessContextBuilder with(String key, Object value) {
        context.put(key, value);
        return this;
    }

    public Map<String, Object> build() {
        return new HashMap<>(context);
    }
}
