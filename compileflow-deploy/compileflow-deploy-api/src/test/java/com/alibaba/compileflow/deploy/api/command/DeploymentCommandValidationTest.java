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
package com.alibaba.compileflow.deploy.api.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.release.ReleaseMetadataKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeploymentCommandValidationTest {
    private static PublishProcessVersionCommand publish(Map<String, String> metadata) {
        return publish(metadata, "release-service");
    }

    private static PublishProcessVersionCommand publish(Map<String, String> metadata, String actor) {
        return new PublishProcessVersionCommand(ProcessRef.version("default", "order.flow", "v1"),
                ProcessDefinition.inline(ProcessModelType.TBBPM, "order.flow", "<bpm/>"), actor, metadata);
    }

    @Test
    void snapshotsBoundedPublicationMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("ticket", "REL-42");
        PublishProcessVersionCommand command = new PublishProcessVersionCommand(ProcessRef.version("default",
                        "order.flow", "v1"), ProcessDefinition.inline(ProcessModelType.TBBPM, "order.flow", "<bpm/>"),
                "release-service", metadata);

        metadata.put("ticket", "changed");

        assertThat(command.getMetadata()).containsExactly(Map.entry("ticket", "REL-42"));
        assertThat(command.getMetadata()).isUnmodifiable();
    }

    @Test
    void keepsArtifactDigestPreconditionsSeparateFromMetadata() {
        String expectedDigest = "a".repeat(64);
        PublishProcessVersionCommand command = new PublishProcessVersionCommand(ProcessRef.version("default",
                        "order.flow", "v1"), ProcessDefinition.inline(ProcessModelType.TBBPM, "order.flow", "<bpm/>"),
                expectedDigest, "release-service", Map.of(ReleaseMetadataKeys.CHANGELOG, "Ready"));

        assertThat(command.getExpectedArtifactDigest()).isEqualTo(expectedDigest);
        assertThat(command.getMetadata()).containsOnlyKeys(ReleaseMetadataKeys.CHANGELOG);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new PublishProcessVersionCommand(ProcessRef.version("default", "order.flow", "v1"),
                    ProcessDefinition.inline(ProcessModelType.TBBPM, "order.flow", "<bpm/>"), "not-a-digest",
                    "release-service", Map.of()))
            .withMessageContaining("expectedArtifactDigest");
    }

    @Test
    void rejectsMetadataThatExceedsThePublicContract() {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int index = 0; index < 65; index++) {
            metadata.put("key-" + index, "value");
        }

        assertThatIllegalArgumentException()
            .isThrownBy(() -> publish(metadata))
            .withMessageContaining("64 entries");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> publish(Map.of("key", "x".repeat(2_049))))
            .withMessageContaining("2048 characters");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> publish(Map.of("key", "value\u0000suffix")))
            .withMessageContaining("must not contain null");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> publish(Map.of("compileflow.unknown", "value")))
            .withMessageContaining("reserved compileflow. namespace");
    }

    @Test
    void rejectsValuesThatCannotBePersisted() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> publish(Map.of("key", "value"), "a".repeat(129)))
            .withMessageContaining("128 characters");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> publish(Map.of("key", "value"), "release\nforged"))
            .withMessageContaining("control characters");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CreateRolloutCommand.canary("release-42",
                    ProcessRef.alias("default", "order.flow", "production"),
                    ProcessRef.version("default", "order.flow", "v1"), 0L, 10_000, "release-service", null))
            .withMessageContaining("between 1 and 9999");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new UpdateCanaryWeightCommand("rollout-1", 10_000, 1L, "release-service"))
            .withMessageContaining("between 1 and 9999");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new PromoteRolloutCommand("rollout-1", 0L, "release-service"))
            .withMessageContaining("expectedRevision must be positive");
    }

    @Test
    void rejectsRolloutTargetForAnotherProcess() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CreateRolloutCommand.allAtOnce("release-42",
                    ProcessRef.alias("default", "order.flow", "production"),
                    ProcessRef.version("default", "payment.flow", "v1"), 0L, "release-service", null))
            .withMessageContaining("same namespace and process code");
    }
}
