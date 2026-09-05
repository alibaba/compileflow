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
package com.alibaba.compileflow.deploy.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.control.repository.InMemoryProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.control.validation.ProcessPublicationValidator;
import com.alibaba.compileflow.deploy.control.validation.ProcessPublicationValidation;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VersionPublicationServiceTest {
    private static final ProcessPublicationValidator PASSING_VALIDATOR =
            (ref, modelType, definition) -> new ProcessPublicationValidation(report(definition.code(),
                            ProcessPreflightReport.ItemStatus.PASS, "lint ok"), java.util.List.of());

    private static ProcessPreflightReport report(String code, ProcessPreflightReport.ItemStatus status, String message) {
        return ProcessPreflightReport
            .builder()
            .code(code)
            .addItem(ProcessPreflightReport.ItemType.LINT, status, 0L, message)
            .build();
    }

    @Test
    void publishesExactContentWithoutCreatingRuntimeState() {
        VersionPublicationService service =
                new VersionPublicationService(new InMemoryProcessVersionRepository(), PASSING_VALIDATOR, 1024);
        ProcessDefinition.Inline definition = ProcessDefinition.inline("order.flow", "<definitions/>");

        ProcessVersionRecord published = service.publish(ProcessRef.version("default", "order.flow", "v1"),
                ProcessModelType.BPMN, definition, Map.of("reason", "release"), "alice");

        assertThat(published.getModelType()).isEqualTo(ProcessModelType.BPMN);
        assertThat(published.getProcessDefinition()).isEqualTo(definition);
        assertThat(published.getArtifactDigest())
            .isEqualTo(ProcessArtifactDigest.compute(ProcessModelType.BPMN, definition, Map.of()));
        assertThat(published.getActor()).isEqualTo("alice");
        assertThat(published.getMetadata()).containsExactlyEntriesOf(Map.of("reason", "release"));
    }

    @Test
    void modelTypeIsScopedToThePublishedVersion() {
        InMemoryProcessVersionRepository repository = new InMemoryProcessVersionRepository();
        VersionPublicationService service = new VersionPublicationService(repository, PASSING_VALIDATOR, 1024);
        ProcessRef.Version first = ProcessRef.version("default", "order.flow", "v1");
        ProcessRef.Version second = ProcessRef.version("default", "order.flow", "v2");

        ProcessVersionRecord tbbpm = service.publish(first, ProcessModelType.TBBPM,
                ProcessDefinition.inline(first.code(), "<bpm/>"), Map.of(), "alice");
        ProcessVersionRecord bpmn = service.publish(second, ProcessModelType.BPMN,
                ProcessDefinition.inline(second.code(), "<definitions/>"), Map.of(), "alice");

        assertThat(tbbpm.getModelType()).isEqualTo(ProcessModelType.TBBPM);
        assertThat(bpmn.getModelType()).isEqualTo(ProcessModelType.BPMN);
    }

    @Test
    void replayReturnsTheOriginalPublicationMetadataAndActor() {
        VersionPublicationService service =
                new VersionPublicationService(new InMemoryProcessVersionRepository(), PASSING_VALIDATOR, 1024);
        ProcessRef.Version ref = ProcessRef.version("default", "order.flow", "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline("order.flow", "<definitions/>");

        ProcessVersionRecord first =
                service.publish(ref, ProcessModelType.BPMN, definition, Map.of("reason", "first"), "alice");
        ProcessVersionRecord replay =
                service.publish(ref, ProcessModelType.BPMN, definition, Map.of("reason", "retry"), "bob");

        assertThat(replay).isSameAs(first);
        assertThat(replay.getActor()).isEqualTo("alice");
        assertThat(replay.getMetadata()).containsExactlyEntriesOf(Map.of("reason", "first"));
    }

    @Test
    void validatesDigestAssertionAndUtf8ByteLimit() {
        VersionPublicationService service =
                new VersionPublicationService(new InMemoryProcessVersionRepository(), PASSING_VALIDATOR, 5);
        ProcessRef.Version ref = ProcessRef.version("default", "order.flow", "v1");

        assertThatThrownBy(() -> service.publish(ref, ProcessModelType.BPMN,
                ProcessDefinition.inline("order.flow", "hello"), "0".repeat(64), Map.of(), "alice"))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH));
        assertThatThrownBy(() -> service.publish(ref, ProcessModelType.BPMN,
                ProcessDefinition.inline("order.flow", "\u4f60\u597d"), Map.of(), "alice"))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void rejectsInvalidDefinitionsBeforePersistence() {
        InMemoryProcessVersionRepository repository = new InMemoryProcessVersionRepository();
        AtomicReference<ProcessModelType> validatedModelType = new AtomicReference<>();
        AtomicReference<ProcessDefinition.Inline> validatedDefinition = new AtomicReference<>();
        ProcessPublicationValidator validator =
                (ref, modelType, definition) -> {
            validatedModelType.set(modelType);
            validatedDefinition.set(definition);
            return new ProcessPublicationValidation(report(definition.code(), ProcessPreflightReport.ItemStatus.FAIL,
                            "schema validation failed"), java.util.List.of());
        };
        VersionPublicationService service = new VersionPublicationService(repository, validator, 1024);
        ProcessRef.Version ref = ProcessRef.version("tenant-a", "order.flow", "v1");
        ProcessDefinition.Inline definition = ProcessDefinition.inline("order.flow", "<invalid/>");

        assertThatThrownBy(() -> service.publish(ref, ProcessModelType.BPMN, definition, Map.of(), "alice"))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(DeploymentErrorCode.INVALID_ARGUMENT);
                assertThat(failure.getMessage()).contains("schema validation failed");
            });

        assertThat(validatedModelType.get()).isEqualTo(ProcessModelType.BPMN);
        assertThat(validatedDefinition.get()).isSameAs(definition);
        assertThat(repository.find("tenant-a", "order.flow", "v1")).isEmpty();
    }

    @Test
    void treatsValidatorFailureAsAnInternalPublicationFailure() {
        ProcessPublicationValidator validator =
                (ref, modelType, definition) -> {
            throw new IllegalStateException("validator unavailable");
        };
        VersionPublicationService service =
                new VersionPublicationService(new InMemoryProcessVersionRepository(), validator, 1024);
        ProcessRef.Version ref = ProcessRef.version("default", "order.flow", "v1");

        assertThatThrownBy(() -> service.publish(ref, ProcessModelType.BPMN,
                ProcessDefinition.inline("order.flow", "<definitions/>"), Map.of(), "alice"))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> {
                assertThat(failure.getErrorCode()).isEqualTo(DeploymentErrorCode.INTERNAL_ERROR);
                assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class);
            });
    }

    @Test
    void publishingAParentPersistsSourceDerivedExactCallBindings() {
        InMemoryProcessVersionRepository repository = new InMemoryProcessVersionRepository();
        VersionPublicationService leafPublisher = new VersionPublicationService(repository, PASSING_VALIDATOR, 1024);
        ProcessRef.Version oldChild = ProcessRef.version("tenant-a", "child.flow", "v3");
        ProcessRef.Version newChild = ProcessRef.version("tenant-a", "child.flow", "v4");
        leafPublisher.publish(oldChild, ProcessModelType.TBBPM,
                ProcessDefinition.inline(oldChild.code(), "<child version='3'/>"), Map.of(), "alice");
        leafPublisher.publish(newChild, ProcessModelType.TBBPM,
                ProcessDefinition.inline(newChild.code(), "<child version='4'/>"), Map.of(), "alice");

        ProcessRef.Version parentRef = ProcessRef.version("tenant-a", "parent.flow", "v1");
        List<ProcessCallBinding> bindings =
                List.of(new ProcessCallBinding("oldChild", oldChild), new ProcessCallBinding("newChild", newChild));
        ProcessPublicationValidator validator =
                (ref, modelType, definition) -> new ProcessPublicationValidation(report(definition.code(),
                                ProcessPreflightReport.ItemStatus.PASS, "lint ok"), bindings);
        VersionPublicationService service = new VersionPublicationService(repository, validator, 1024);
        ProcessVersionRecord parent = service.publish(parentRef, ProcessModelType.TBBPM,
                ProcessDefinition.inline(parentRef.code(), "<parent/>"), Map.of(), "alice");

        assertThat(parent.getCallBindings()).containsExactlyElementsOf(bindings);
        assertThat(parent.toArtifact().getCallBindings()).containsOnlyKeys("oldChild", "newChild");
    }

    @Test
    void rejectsParentWhenAnExactChildVersionIsMissing() {
        InMemoryProcessVersionRepository repository = new InMemoryProcessVersionRepository();
        ProcessRef.Version parent = ProcessRef.version("tenant-a", "parent.flow", "v1");
        ProcessRef.Version missing = ProcessRef.version("tenant-a", "child.flow", "v9");
        ProcessPublicationValidator validator =
                (ref, modelType, definition) -> new ProcessPublicationValidation(report(definition.code(),
                                ProcessPreflightReport.ItemStatus.PASS, "lint ok"),
                        List.of(new ProcessCallBinding("child", missing)));
        VersionPublicationService service = new VersionPublicationService(repository, validator, 1024);

        assertThatThrownBy(() -> service.publish(parent, ProcessModelType.TBBPM,
                ProcessDefinition.inline(parent.code(), "<parent/>"), Map.of(), "alice"))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.DEPENDENCY_NOT_FOUND));
        assertThat(repository.find(parent.namespace(), parent.code(), parent.version())).isEmpty();
    }

    @Test
    void rejectsCrossFormatCallGraphBeforePublishingParent() {
        InMemoryProcessVersionRepository repository = new InMemoryProcessVersionRepository();
        ProcessRef.Version child = ProcessRef.version("tenant-a", "child.flow", "v1");
        new VersionPublicationService(repository, PASSING_VALIDATOR, 1024)
            .publish(child, ProcessModelType.BPMN, ProcessDefinition.inline(child.code(), "<child/>"), Map.of(), "alice");
        ProcessRef.Version parent = ProcessRef.version("tenant-a", "parent.flow", "v1");
        ProcessPublicationValidator validator =
                (ref, modelType, definition) -> new ProcessPublicationValidation(report(definition.code(),
                                ProcessPreflightReport.ItemStatus.PASS, "lint ok"),
                        List.of(new ProcessCallBinding("child", child)));
        VersionPublicationService service = new VersionPublicationService(repository, validator, 1024);

        assertThatThrownBy(() -> service.publish(parent, ProcessModelType.TBBPM,
                ProcessDefinition.inline(parent.code(), "<parent/>"), Map.of(), "alice"))
            .isInstanceOfSatisfying(DeploymentException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(DeploymentErrorCode.INVALID_ARGUMENT));
        assertThat(repository.find(parent.namespace(), parent.code(), parent.version())).isEmpty();
    }
}
