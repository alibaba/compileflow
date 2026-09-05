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

import com.alibaba.compileflow.engine.core.lifecycle.OperationGate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Isolates the steady-state admission cost of lifecycle strategies.
 *
 * <p>The atomic-state case deliberately provides weaker semantics: it rejects work observed after
 * shutdown but does not drain admitted operations. It is included as a lower bound, not as an
 * interchangeable implementation. Override the thread count with JMH {@code -t}.
 *
 * @author yusu
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class OperationGateBenchmark {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final OperationGate operationGate = new OperationGate("benchmark");
    private final LongAdderCounter longAdderCounter = new LongAdderCounter();
    private final SimpleAtomicCounter simpleAtomicCounter = new SimpleAtomicCounter();
    private final PackedAtomicCounter packedAtomicCounter = new PackedAtomicCounter();
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

    /**
     * Measures a volatile state read without in-flight draining semantics.
     *
     * @return the observed state
     */
    @Benchmark
    public boolean atomicStateOnly() {
        return closed.get();
    }

    /**
     * Measures exact admission and release on the lock-free steady-state path.
     *
     * @return the gate used by the operation
     */
    @Benchmark
    public OperationGate exactOperationGate() {
        operationGate.enter();
        try {
            return operationGate;
        } finally {
            operationGate.exit();
        }
    }

    /**
     * Measures the scalable LongAdder alternative whose aggregate is not an atomic drain
     * snapshot.
     *
     * @return the counter used by the operation
     */
    @Benchmark
    public LongAdderCounter longAdderCounter() {
        longAdderCounter.enter();
        try {
            return longAdderCounter;
        } finally {
            longAdderCounter.exit();
        }
    }

    /**
     * Measures the simplest exact AtomicBoolean plus AtomicInteger strategy.
     *
     * @return the counter used by the operation
     */
    @Benchmark
    public SimpleAtomicCounter simpleAtomicCounter() {
        simpleAtomicCounter.enter();
        try {
            return simpleAtomicCounter;
        } finally {
            simpleAtomicCounter.exit();
        }
    }

    /**
     * Measures the former single-word exact admission counter.
     *
     * @return the counter used by the operation
     */
    @Benchmark
    public PackedAtomicCounter packedAtomicCounter() {
        packedAtomicCounter.enter();
        try {
            return packedAtomicCounter;
        } finally {
            packedAtomicCounter.exit();
        }
    }

    /**
     * Measures the former fair read-lock lifecycle strategy.
     *
     * @return the lifecycle lock used by the operation
     */
    @Benchmark
    public ReentrantReadWriteLock fairReadLock() {
        Lock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return readWriteLock;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Benchmark copy of the former packed atomic hot path. Shutdown methods are intentionally
     * omitted because this benchmark measures steady-state admission only.
     */
    public static final class PackedAtomicCounter {
        private static final int STATE_SHIFT = 62;
        private static final long COUNT_MASK = (1L << STATE_SHIFT) - 1L;
        private final AtomicLong state = new AtomicLong();
        private final ThreadLocal<Integer> localDepth = ThreadLocal.withInitial(() -> 0);

        private void enter() {
            Integer depth = localDepth.get();
            if (depth > 0) {
                localDepth.set(depth + 1);
                return;
            }
            while (true) {
                long current = state.get();
                if ((current & COUNT_MASK) == COUNT_MASK) {
                    throw new IllegalStateException("too many active operations");
                }
                if (state.compareAndSet(current, current + 1L)) {
                    localDepth.set(1);
                    return;
                }
            }
        }

        private void exit() {
            Integer depth = localDepth.get();
            if (depth == 0) {
                throw new IllegalStateException("not entered");
            }
            if (depth > 1) {
                localDepth.set(depth - 1);
                return;
            }
            localDepth.set(0);
            if (state.getAndDecrement() <= 0L) {
                state.incrementAndGet();
                throw new IllegalStateException("underflow");
            }
        }
    }

    /**
     * Benchmark-only copy of the scalable LongAdder hot path. Its close-side sum is deliberately
     * omitted because it is not an atomic snapshot and therefore is not equivalent to the exact
     * gate.
     */
    public static final class LongAdderCounter {
        private final AtomicBoolean shutdown = new AtomicBoolean();
        private final LongAdder active = new LongAdder();
        private final ThreadLocal<Integer> localDepth = ThreadLocal.withInitial(() -> 0);

        private void enter() {
            Integer depth = localDepth.get();
            if (depth > 0) {
                localDepth.set(depth + 1);
                return;
            }
            if (shutdown.get()) {
                throw new IllegalStateException("closed");
            }
            active.increment();
            if (shutdown.get()) {
                active.decrement();
                throw new IllegalStateException("closed");
            }
            localDepth.set(1);
        }

        private void exit() {
            Integer depth = localDepth.get();
            if (depth == 0) {
                throw new IllegalStateException("not entered");
            }
            if (depth > 1) {
                localDepth.set(depth - 1);
                return;
            }
            localDepth.set(0);
            active.decrement();
        }
    }

    /**
     * Benchmark copy of the Quarkus-style single active-request counter.
     */
    public static final class SimpleAtomicCounter {
        private final AtomicBoolean shutdown = new AtomicBoolean();
        private final AtomicInteger active = new AtomicInteger();
        private final ThreadLocal<Integer> localDepth = ThreadLocal.withInitial(() -> 0);

        private void enter() {
            Integer depth = localDepth.get();
            if (depth > 0) {
                localDepth.set(depth + 1);
                return;
            }
            if (shutdown.get()) {
                throw new IllegalStateException("closed");
            }
            active.incrementAndGet();
            if (shutdown.get()) {
                active.decrementAndGet();
                throw new IllegalStateException("closed");
            }
            localDepth.set(1);
        }

        private void exit() {
            Integer depth = localDepth.get();
            if (depth == 0) {
                throw new IllegalStateException("not entered");
            }
            if (depth > 1) {
                localDepth.set(depth - 1);
                return;
            }
            localDepth.set(0);
            if (active.getAndDecrement() <= 0) {
                active.incrementAndGet();
                throw new IllegalStateException("underflow");
            }
        }
    }
}
