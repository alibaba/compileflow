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
package com.alibaba.compileflow.examples.durable;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.durable.spi.admission.DurableVersionDefinitionSource;
import com.alibaba.compileflow.durable.spi.outbox.DurableOutboxSink;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

/**
 * Minimal greenfield application embedding the content-addressed PostgreSQL Durable Kernel.
 *
 * @author yusu
 */
@SpringBootApplication
public class DurableSampleApplication {
    public static final ProcessRef.Version PROCESS = ProcessRef.version("default", "durable.sample.order", "v1");

    public static void main(String[] args) {
        SpringApplication.run(DurableSampleApplication.class, args);
    }

    @Bean
    DurableVersionDefinitionSource durableVersionDefinitionSource() throws IOException {
        String content =
                new String(new ClassPathResource("flows/durable-order.bpm").getContentAsByteArray(),
                        StandardCharsets.UTF_8);
        ProcessDefinition.Inline definition = ProcessDefinition.inline(ProcessModelType.TBBPM, PROCESS.code(), content);
        return requested -> requested.equals(PROCESS)
                ? Optional.of(new DurableVersionDefinitionSource.VersionDefinition(definition))
                : Optional.empty();
    }

    @Bean
    RecordingOutboxSink durableOutboxSink() {
        return new RecordingOutboxSink();
    }

    /**
     * Demo Effect Action resolved by the current deployment, never persisted as Kernel identity.
     */
    public static final class DemoChargeAction {
        private static final List<String> CHARGED_ORDER_IDS = new CopyOnWriteArrayList<>();

        public String charge(String orderId) {
            String value = Objects.requireNonNull(orderId, "orderId").trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("orderId must not be blank");
            }
            CHARGED_ORDER_IDS.add(value);
            return "rcpt-" + value;
        }

        static List<String> chargedOrderIds() {
            return List.copyOf(CHARGED_ORDER_IDS);
        }

        static void reset() {
            CHARGED_ORDER_IDS.clear();
        }
    }

    /**
     * In-memory authenticated-channel stand-in used only by the example.
     */
    public static final class RecordingOutboxSink implements DurableOutboxSink {
        private final Set<UUID> delivered = ConcurrentHashMap.newKeySet();
        private final LinkedBlockingQueue<String> waitTokens = new LinkedBlockingQueue<>();

        @Override
        public void deliver(OutboundEvent event) {
            if (!delivered.add(event.eventId()) || !"WAIT_COMMITTED".equals(event.eventType())) {
                return;
            }
            Object token = event.payload().get("waitToken");
            if (token instanceof String value) {
                waitTokens.add(value);
            }
        }

        public String awaitWaitToken(Duration timeout) throws InterruptedException {
            String token = waitTokens.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (token == null) {
                throw new IllegalStateException("Timed out waiting for a Durable Wait token");
            }
            return token;
        }

        void reset() {
            delivered.clear();
            waitTokens.clear();
        }
    }
}
