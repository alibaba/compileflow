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
package com.alibaba.compileflow.engine.core.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicAliasSelectorTest {
    private static ProcessAliasRoute route(String namespace, String code, String alias, String candidateVersion,
            int candidateWeightBps, String routingKey) {
        ProcessRef.Alias ref = ProcessRef.alias(namespace, code, alias);
        return new ProcessAliasRoute(ref, ProcessRef.version(namespace, code, "v1"),
                candidateVersion == null ? null : ProcessRef.version(namespace, code, candidateVersion),
                candidateWeightBps, null, 1L);
    }

    private static GoldenVector vector(String namespace, String code, String alias, String candidateVersion,
            String routingKey, String digest, int bucket) {
        return new GoldenVector(namespace, code, alias, candidateVersion, routingKey, digest, bucket);
    }

    @Test
    void matchesCrossLanguageGoldenVectors() {
        List<GoldenVector> vectors = List.of(vector("default", "order.process", "production", "v2", "user-1",
                        "13d187c7895084877494d3f6021e413df3bdb168bd68f49ae3e99b79b530d1c3", 4951),
                vector("default", "order.process", "production", "v2", "user-2",
                        "8a212b37afa9818e8768448cc8cf585463eea8a1096f3381aed02c88a888a242", 4014),
                vector("tenant-a", "payment.approve", "canary", "2026.07.1", "customer-42",
                        "e249773e77d926aec0c6d5bb3226e6f34cc6c03aab4ddd089d5ed1fdba42193c", 9038),
                vector("tenant-a", "payment.approve", "canary", "2026.07.2", "customer-42",
                        "c3ca08fd3ac5fe8c407f83507b2a551b663775a2411627479ba1e74ccdd9e663", 2412),
                vector("default", "order.process", "production", "v2", " 用户-甲 ",
                        "bc79013a72b8179b819699fc7dced3e0567976a420994e2c5ca7ac401729c8c8", 9579),
                vector("default", "order.process", "production", "v2", "é",
                        "522eb9004fa1dd5a274a9666c8273f37f56f05a0f3c7ceeb779b1c2735c3c41a", 7514),
                vector("default", "order.process", "production", "v2", "e\u0301",
                        "505d8b39e3485d48ac94a9454a4e3152676ce12c3e7fcd8a48d71e0d666ed218", 3128),
                vector("default", "a", "prod", "1", "0",
                        "0bcf8ac1e6f054c173e1488ec98e5ef80a74f2c1f28f804b0d4a4626b80d93f4", 4321),
                vector("n", "a.b-c_1", "stage", "candidate-0001", "00000000-0000-0000-0000-000000000000",
                        "5b5e1224e7ade9836cece8e6fe06787be5f612de9732cfb0ae70f0c4d7a763f9", 867),
                vector("tenant-999", "very.long.process.code", "blue", "sha256-abcdef", "key/with?symbols=&%",
                        "adbfad174240b046b6e49d8a5be05d4393ac33a1bfc9f20434355c7de2b3ef3b", 6966),
                vector("default", "order.process", "production", "v3", "user-1",
                        "a3506484ec403f76f72170c1ca43d2ea2a884c56549639d8c48154514ebb5c11", 246),
                vector("default", "order.process", "staging", "v2", "user-1",
                        "082b2bc1a2548168703deb9fd184bfa734af342076c60e7a22ec83936dd0e7aa", 1336));

        for (GoldenVector vector : vectors) {
            ProcessAliasRoute route = route(vector.namespace(), vector.code(), vector.alias(), vector.candidateVersion(),
                    5000, vector.routingKey());
            assertThat(HexFormat.of().formatHex(DeterministicAliasSelector.hash(route, vector.routingKey())))
                .as("hash for %s", vector)
                .isEqualTo(vector.digest());
            assertThat(DeterministicAliasSelector.bucket(route, vector.routingKey()))
                .as("bucket for %s", vector)
                .isEqualTo(vector.bucket());
        }
    }

    @Test
    void candidateSelectionRequiresAnEffectiveCohortKey() {
        ProcessAliasRoute route = route("default", "order.process", "production", "v2", 9999, null);

        assertThatNullPointerException()
            .isThrownBy(() -> DeterministicAliasSelector.select(route, null))
            .withMessage("effective routing key is required for candidate selection");
    }

    @Test
    void increasingWeightOnlyAddsCandidateCohorts() {
        ProcessAliasRoute below = route("default", "order.process", "production", "v2", 4000, "user-1");
        ProcessAliasRoute crossing = route("default", "order.process", "production", "v2", 5000, "user-1");
        ProcessAliasRoute above = route("default", "order.process", "production", "v2", 9000, "user-1");

        assertThat(DeterministicAliasSelector.bucket(below, "user-1")).isEqualTo(4951);
        assertThat(DeterministicAliasSelector.select(below, "user-1")).isEqualTo(ProcessAliasTarget.STABLE);
        assertThat(DeterministicAliasSelector.select(crossing, "user-1")).isEqualTo(ProcessAliasTarget.CANDIDATE);
        assertThat(DeterministicAliasSelector.select(above, "user-1")).isEqualTo(ProcessAliasTarget.CANDIDATE);
    }

    @Test
    void changingCandidateVersionRotatesTheCohort() {
        ProcessAliasRoute versionTwo = route("default", "order.process", "production", "v2", 1000, "user-1");
        ProcessAliasRoute versionThree = route("default", "order.process", "production", "v3", 1000, "user-1");

        assertThat(DeterministicAliasSelector.bucket(versionTwo, "user-1")).isEqualTo(4951);
        assertThat(DeterministicAliasSelector.bucket(versionThree, "user-1")).isEqualTo(246);
        assertThat(DeterministicAliasSelector.select(versionTwo, "user-1")).isEqualTo(ProcessAliasTarget.STABLE);
        assertThat(DeterministicAliasSelector.select(versionThree, "user-1")).isEqualTo(ProcessAliasTarget.CANDIDATE);
    }

    private record GoldenVector(String namespace, String code, String alias, String candidateVersion, String routingKey,
            String digest, int bucket) {}
}
