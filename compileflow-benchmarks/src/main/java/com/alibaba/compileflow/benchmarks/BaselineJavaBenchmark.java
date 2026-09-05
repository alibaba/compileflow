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
package com.alibaba.compileflow.benchmarks;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Hand-written Java baseline for the {@code value + 2} flow used by
 * {@link CompileFlowExecuteBenchmark}.
 *
 * <p>The CompileFlow hot-path number should approach this baseline after the
 * engine is warm. Quoting a single ratio (e.g. {@code 1.3x baseline}) is not
 * meaningful without the JDK, OS, CPU, and fork configuration documented in the
 * benchmarks README. Always publish the raw JSON output alongside any
 * summary.
 *
 * @author yusu
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 5)
@Fork(value = 1)
public class BaselineJavaBenchmark {
    /**
     * Equivalent of {@code hello.bpm}: read {@code value} from a context map,
     * compute {@code value + 2}, write {@code result} back.
     *
     * @return the populated context map to keep allocation and map writes observable
     */
    @Benchmark
    public Map<String, Object> baselineAddTwo() {
        Map<String, Object> context = new HashMap<>();
        context.put("value", 40);
        int result = ((Integer) context.get("value")) + 2;
        context.put("result", result);
        return context;
    }
}
