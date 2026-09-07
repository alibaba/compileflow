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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import com.alibaba.compileflow.durable.api.DurableProcessEngine;
import com.alibaba.compileflow.durable.api.model.ProcessRunId;
import com.alibaba.compileflow.durable.api.model.ProcessRunStatus;
import com.alibaba.compileflow.durable.api.model.ProcessRun;
import com.alibaba.compileflow.durable.api.model.WaitToken;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@EnabledIf("postgresAvailable")
class DurableSampleApplicationTest {
    private static final boolean EXTERNAL_POSTGRES_CONFIGURED = hasText(System.getenv(
            "COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_URL"))
            && hasText(System.getenv("COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_USERNAME"))
            && System.getenv("COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_PASSWORD") != null;
    private static final PostgreSQLContainer POSTGRES_CONTAINER = createPostgresContainer();
    private final DurableProcessEngine processEngine;
    private final DurableSampleApplication.RecordingOutboxSink outboxSink;

    @Autowired
    DurableSampleApplicationTest(DurableProcessEngine processEngine,
            DurableSampleApplication.RecordingOutboxSink outboxSink) {
        this.processEngine = processEngine;
        this.outboxSink = outboxSink;
    }

    @BeforeEach
    void resetExampleAdapters() {
        DurableSampleApplication.DemoChargeAction.reset();
        outboxSink.reset();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES_CONTAINER != null) {
            POSTGRES_CONTAINER.start();
        }
        registry.add("spring.datasource.url", DurableSampleApplicationTest::postgresUrl);
        registry.add("spring.datasource.username", DurableSampleApplicationTest::postgresUsername);
        registry.add("spring.datasource.password", DurableSampleApplicationTest::postgresPassword);
    }

    @AfterAll
    static void stopPostgresContainer() {
        if (POSTGRES_CONTAINER != null && POSTGRES_CONTAINER.isRunning()) {
            POSTGRES_CONTAINER.stop();
        }
    }

    static boolean postgresAvailable() {
        if (EXTERNAL_POSTGRES_CONFIGURED) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    private static String postgresUrl() {
        return POSTGRES_CONTAINER == null
                ? System.getenv("COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_URL")
                : POSTGRES_CONTAINER.getJdbcUrl();
    }

    private static String postgresUsername() {
        return POSTGRES_CONTAINER == null
                ? System.getenv("COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_USERNAME")
                : POSTGRES_CONTAINER.getUsername();
    }

    private static String postgresPassword() {
        return POSTGRES_CONTAINER == null
                ? System.getenv("COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_PASSWORD")
                : POSTGRES_CONTAINER.getPassword();
    }

    private static PostgreSQLContainer createPostgresContainer() {
        if (EXTERNAL_POSTGRES_CONFIGURED) {
            return null;
        }
        return new PostgreSQLContainer(DockerImageName
            .parse("postgres:18.6-alpine3.24@sha256:d3e1620b530c944afa6e887d22eb899824da68e19c52024bf98f5220c88a65b2")
            .asCompatibleSubstituteFor("postgres"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Test
    void persistsTimerEffectWaitCompletionAndRunCompletion() throws InterruptedException {
        ProcessRun started =
                processEngine.start(ProcessRunId.random(), DurableSampleApplication.PROCESS,
                        Map.of("orderId", "order-42"));

        await()
            .atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> assertThat(DurableSampleApplication.DemoChargeAction.chargedOrderIds())
                .contains("order-42"));

        String waitToken = outboxSink.awaitWaitToken(Duration.ofSeconds(20));
        await()
            .atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> assertThat(processEngine.getRun(started.runId()))
                .get()
                .extracting(ProcessRun::status)
                .isEqualTo(ProcessRunStatus.WAITING));

        processEngine.completeWait(new WaitToken(waitToken), Map.of("approved", true));

        await()
            .atMost(Duration.ofSeconds(20))
            .untilAsserted(() -> assertThat(processEngine.getRun(started.runId()))
                .get()
                .extracting(ProcessRun::status)
                .isEqualTo(ProcessRunStatus.SUCCEEDED));
    }

    @Test
    void validatesExternallyMigratedSchemaWithoutDdl() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(DurableSampleApplication.class)
            .run("--spring.main.web-application-type=none", "--spring.datasource.url=" + postgresUrl(),
                    "--spring.datasource.username=" + postgresUsername(),
                    "--spring.datasource.password=" + postgresPassword(), "--compileflow.durable.database.migrate=false",
                    "--compileflow.durable.worker.enabled=false")) {
            assertThat(context.isActive()).isTrue();
            assertThat(context.getEnvironment().getProperty("compileflow.durable.database.migrate", Boolean.class)).isFalse();
            assertThat(context.getEnvironment().getProperty("compileflow.durable.worker.enabled", Boolean.class)).isFalse();
        }
    }
}
