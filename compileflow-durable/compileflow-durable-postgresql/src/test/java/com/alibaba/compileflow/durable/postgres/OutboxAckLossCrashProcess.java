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
package com.alibaba.compileflow.durable.postgres;

import com.alibaba.compileflow.durable.runtime.codec.DurableKernelJsonCodec;
import com.alibaba.compileflow.durable.runtime.worker.DurableLeaseRenewer;
import com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisher;
import com.alibaba.compileflow.durable.runtime.worker.DurableOutboxPublisherOptions;
import com.alibaba.compileflow.durable.spi.outbox.DurableOutboxSink;
import com.alibaba.compileflow.durable.spi.store.DurableStore;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;

/**
 * Child JVM held inside a sink after external durable acceptance so its parent can SIGKILL it.
 *
 * @author yusu
 */
final class OutboxAckLossCrashProcess {
    private OutboxAckLossCrashProcess() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected accepted-event and marker paths");
        }
        Path acceptedEvent = Path.of(arguments[0]);
        Path acceptedMarker = Path.of(arguments[1]);
        DurableStore store = new PostgresDurableStore(
                new DriverManagerDataSource(requiredEnvironment("COMPILEFLOW_DURABLE_POSTGRES_URL"),
                        environmentOrDefault("COMPILEFLOW_DURABLE_POSTGRES_USER", "postgres"),
                        environmentOrDefault("COMPILEFLOW_DURABLE_POSTGRES_PASSWORD", "postgres")));
        Duration lease = Duration.ofSeconds(1);
        try (DurableLeaseRenewer renewer = new DurableLeaseRenewer(store, lease)) {
            DurableOutboxPublisher publisher = new DurableOutboxPublisher(store,
                    event -> acceptAndBlock(event, acceptedEvent, acceptedMarker),
                    new DurableOutboxPublisherOptions("ack-loss-child", Duration.ofMillis(100), Duration.ofSeconds(1),
                            100), renewer);
            if (!publisher.runOnce()) {
                throw new IllegalStateException("No committed Outbox event was available");
            }
        }
    }

    private static void acceptAndBlock(DurableOutboxSink.OutboundEvent event, Path acceptedEvent, Path acceptedMarker)
            throws Exception {
        String accepted = String.join("\n", event.eventId().toString(), event.runId().value(),
                        event.processVersion().namespace(), event.processCode(), event.processVersion().version(),
                        event.eventType(),
                        Base64.getEncoder().encodeToString(new DurableKernelJsonCodec().encode(event.payload()))) + "\n";
        Files.writeString(acceptedEvent, accepted, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        force(acceptedEvent);
        Files.writeString(acceptedMarker, "durably-accepted\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        force(acceptedMarker);
        new CountDownLatch(1).await();
    }

    private static void force(Path path) throws Exception {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
