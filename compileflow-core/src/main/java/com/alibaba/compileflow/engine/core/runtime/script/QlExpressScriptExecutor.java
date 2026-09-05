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
package com.alibaba.compileflow.engine.core.runtime.script;

import com.alibaba.compileflow.engine.spi.script.ScriptProgram;
import com.alibaba.compileflow.engine.spi.script.ScriptException;
import com.alibaba.compileflow.engine.spi.script.ScriptExecutor;
import com.alibaba.compileflow.engine.spi.script.ScriptProgramSpec;
import com.alibaba.qlexpress4.ClassSupplier;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.api.parsecache.LoadedParseCache;
import com.alibaba.qlexpress4.exception.QLTimeoutException;
import com.alibaba.qlexpress4.runtime.context.MapExpressContext;
import com.alibaba.qlexpress4.security.QLSecurityStrategy;
import java.lang.reflect.Array;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Core-provided QLExpress executor with a fixed isolated language profile.
 *
 * @author yusu
 */
public final class QlExpressScriptExecutor implements ScriptExecutor, AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final int MAX_ARRAY_LENGTH = 10_000;
    private final Express4Runner runner;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Creates an executor for one process engine.
     *
     * @param classLoader  class loader used only for provider-internal type resolution
     */
    public QlExpressScriptExecutor(ClassLoader classLoader) {
        ClassLoader loader = Objects.requireNonNull(classLoader, "classLoader");
        this.runner = new Express4Runner(InitOptions
            .builder()
            .classSupplier(classSupplier(loader))
            .securityStrategy(QLSecurityStrategy.isolation())
            .build());
        registerSafeFunctions(runner);
    }

    private static ClassSupplier classSupplier(ClassLoader classLoader) {
        return className -> {
            try {
                return Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                return null;
            }
        };
    }

    private static void registerSafeFunctions(Express4Runner runner) {
        Function<Object, Integer> size = QlExpressScriptExecutor::sizeOf;
        if (!runner.addFunction("size", size)) {
            throw new IllegalStateException("Failed to register the built-in QL size function");
        }
    }

    private static int sizeOf(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        if (value instanceof CharSequence chars) {
            return chars.length();
        }
        if (value != null && value.getClass().isArray()) {
            return Array.getLength(value);
        }
        throw new IllegalArgumentException("size() requires a collection, map, string, or array");
    }

    @Override
    public String name() {
        return "qlexpress";
    }

    @Override
    public void validate(ScriptProgramSpec spec) {
        compile(requireSource(spec, ScriptException.Kind.INVALID_SOURCE), ScriptException.Kind.INVALID_SOURCE);
    }

    @Override
    public ScriptProgram compile(ScriptProgramSpec spec) {
        return new QlScriptProgram(
                compile(requireSource(spec, ScriptException.Kind.COMPILATION_FAILED),
                        ScriptException.Kind.COMPILATION_FAILED));
    }

    private static String requireSource(ScriptProgramSpec spec, ScriptException.Kind failureKind) {
        ScriptProgramSpec exact = Objects.requireNonNull(spec, "spec");
        if (!"qlexpress".equals(exact.language())) {
            throw new ScriptException(failureKind,
                    "QL script executor only compiles language 'qlexpress', not '" + exact.language() + "'");
        }
        return exact.source();
    }

    private LoadedParseCache compile(String source, ScriptException.Kind failureKind) {
        if (closed.get()) {
            throw new ScriptException(ScriptException.Kind.COMPILATION_FAILED, "QL script executor is closed");
        }
        String exactSource = Objects.requireNonNull(source, "source");
        try {
            return runner.loadSerializableCache(runner.parseToSerializableCache(exactSource));
        } catch (RuntimeException failure) {
            throw new ScriptException(failureKind, "QL script compilation failed", failure);
        }
    }

    @Override
    public Object evaluate(ScriptProgram script, Map<String, Object> context) {
        if (closed.get()) {
            throw new ScriptException(ScriptException.Kind.EVALUATION_FAILED, "QL script executor is closed");
        }
        if (!(Objects.requireNonNull(script, "script") instanceof QlScriptProgram program)) {
            throw new ScriptException(ScriptException.Kind.EVALUATION_FAILED,
                    "Script program was not created by the QL executor");
        }
        Map<String, Object> variables = new HashMap<>(Objects.requireNonNull(context, "context"));
        QLOptions options = QLOptions
            .builder()
            .timeoutMillis(TIMEOUT.toMillis())
            .maxArrLength(MAX_ARRAY_LENGTH)
            .build();
        try {
            return runner.execute(program.compiled(), new MapExpressContext(variables), options).getResult();
        } catch (QLTimeoutException failure) {
            throw new ScriptException(ScriptException.Kind.TIMED_OUT, "QL script evaluation timed out", failure);
        } catch (RuntimeException failure) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new ScriptException(ScriptException.Kind.CANCELLED, "QL script evaluation was interrupted",
                        failure);
            }
            throw new ScriptException(ScriptException.Kind.EVALUATION_FAILED, "QL script evaluation failed", failure);
        }
    }

    /**
     * Prevents further use when the owning engine closes.
     */
    @Override
    public void close() {
        closed.set(true);
    }

    private record QlScriptProgram(LoadedParseCache compiled) implements ScriptProgram {
        private QlScriptProgram {
            Objects.requireNonNull(compiled, "compiled");
        }

        @Override
        public String language() {
            return "qlexpress";
        }
    }
}
