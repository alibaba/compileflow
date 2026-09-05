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

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessText;
import com.alibaba.compileflow.deploy.api.artifact.ProcessArtifactDigest;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.release.DeploymentAudit;
import com.alibaba.compileflow.deploy.api.release.ReleaseMetadata;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.control.repository.ProcessVersionRepository;
import com.alibaba.compileflow.deploy.control.validation.ProcessPublicationValidator;
import com.alibaba.compileflow.deploy.control.validation.ProcessPublicationValidation;
import com.alibaba.compileflow.deploy.api.artifact.ProcessCallBinding;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Creates immutable published-version facts without installing a node-local runtime.
 *
 * @author yusu
 */
public final class VersionPublicationService {
    private static final int MAX_VALIDATION_DETAIL_CHARS = 512;
    private final ProcessVersionRepository repository;
    private final ProcessPublicationValidator publicationValidator;
    private final int maxDefinitionBytes;

    public VersionPublicationService(ProcessVersionRepository repository,
            ProcessPublicationValidator publicationValidator, int maxDefinitionBytes) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.publicationValidator = Objects.requireNonNull(publicationValidator, "publicationValidator");
        if (maxDefinitionBytes <= 0) {
            throw new IllegalArgumentException("maxDefinitionBytes must be positive");
        }
        this.maxDefinitionBytes = maxDefinitionBytes;
    }

    private static ProcessModelType requireModelType(ProcessModelType modelType, ProcessRef.Version ref) {
        if (modelType == null) {
            throw DeploymentException.fromRef(DeploymentErrorCode.INVALID_ARGUMENT, "modelType must not be null", ref);
        }
        return modelType;
    }

    private static String validationFailureMessage(ProcessPreflightReport report) {
        String detail = report
            .getItems()
            .stream()
            .filter(item -> item.getStatus() != ProcessPreflightReport.ItemStatus.PASS)
            .map(ProcessPreflightReport.Item::getMessage)
            .filter(StringUtils::isNotBlank)
            .findFirst()
            .map(String::trim)
            .orElse(null);
        if (detail == null) {
            return "Process definition failed publication validation";
        }
        if (detail.codePointCount(0, detail.length()) > MAX_VALIDATION_DETAIL_CHARS) {
            detail = ProcessText.truncateCodePoints(detail, MAX_VALIDATION_DETAIL_CHARS) + "...";
        }
        return "Process definition failed publication validation: " + detail;
    }

    public ProcessVersionRecord publish(ProcessRef.Version ref, ProcessModelType modelType,
            ProcessDefinition.Inline definition, Map<String, String> metadata, String actor) {
        return publish(ref, modelType, definition, null, metadata, actor);
    }

    public ProcessVersionRecord publish(ProcessRef.Version ref, ProcessModelType modelType,
            ProcessDefinition.Inline definition, String expectedArtifactDigest, Map<String, String> metadata,
            String actor) {
        ProcessRef.Version versionRef = Objects.requireNonNull(ref, "ref");
        ProcessDefinition.Inline source = Objects.requireNonNull(definition, "definition");
        ProcessModelType effectiveModelType = requireModelType(modelType, versionRef);
        if (!versionRef.code().equals(source.code())) {
            throw DeploymentException.fromRef(DeploymentErrorCode.INVALID_ARGUMENT,
                    "Version reference and definition code must match", versionRef);
        }
        byte[] contentBytes = source.content().getBytes(StandardCharsets.UTF_8);
        if (contentBytes.length > maxDefinitionBytes) {
            throw DeploymentException.fromRef(DeploymentErrorCode.INVALID_ARGUMENT,
                    "Process definition exceeds the publication size limit of " + maxDefinitionBytes + " bytes",
                    versionRef);
        }

        String normalizedActor;
        String normalizedExpectedDigest;
        Map<String, String> immutableMetadata;
        try {
            normalizedActor = DeploymentAudit.requireActor(actor);
            normalizedExpectedDigest = ProcessIdentifiers.optionalSha256(expectedArtifactDigest,
                    "expectedArtifactDigest");
            immutableMetadata = new LinkedHashMap<>(ReleaseMetadata.immutableCopy(metadata));
        } catch (IllegalArgumentException failure) {
            throw DeploymentException.fromRef(DeploymentErrorCode.INVALID_ARGUMENT, failure.getMessage(), versionRef);
        }

        ProcessVersionRecord existing =
                repository.find(versionRef.namespace(), versionRef.code(), versionRef.version()).orElse(null);
        if (existing != null) {
            if (existing.getModelType() != effectiveModelType || !existing.getProcessDefinition().equals(source)) {
                throw DeploymentException.fromRef(DeploymentErrorCode.VERSION_CONFLICT,
                        "Process Version is already published with a different definition", versionRef);
            }
            requireExpectedDigest(versionRef, normalizedExpectedDigest, existing.getArtifactDigest());
            return existing;
        }

        List<ProcessCallBinding> callBindings = validateDefinition(versionRef, effectiveModelType, source);
        Map<String, ProcessRef.Version> targets = callBindings
            .stream()
            .collect(java.util.stream.Collectors.toMap(ProcessCallBinding::callSiteId, ProcessCallBinding::target));
        String digest = ProcessArtifactDigest.compute(effectiveModelType, source, targets);
        requireExpectedDigest(versionRef, normalizedExpectedDigest, digest);

        long createdAt = repository.currentTimeMillis();
        ProcessVersionRecord candidate = ProcessVersionRecord
            .builder()
            .namespace(versionRef.namespace())
            .code(versionRef.code())
            .version(versionRef.version())
            .modelType(effectiveModelType)
            .processDefinition(source)
            .artifactDigest(digest)
            .callBindings(callBindings)
            .metadata(immutableMetadata)
            .actor(normalizedActor)
            .createdAt(createdAt)
            .build();
        return repository.save(candidate);
    }

    private static void requireExpectedDigest(ProcessRef.Version ref, String expected, String actual) {
        if (expected != null && !Objects.equals(expected, actual)) {
            throw DeploymentException.fromRef(DeploymentErrorCode.ARTIFACT_DIGEST_MISMATCH,
                    "Provided artifact digest does not match the executable process artifact", ref);
        }
    }

    private List<ProcessCallBinding> validateDefinition(ProcessRef.Version ref, ProcessModelType modelType,
            ProcessDefinition.Inline definition) {
        ProcessPublicationValidation validation;
        try {
            validation = Objects.requireNonNull(publicationValidator.validate(ref, modelType, definition),
                    "ProcessPublicationValidator must return a validation result");
        } catch (IllegalArgumentException failure) {
            throw DeploymentException.fromRef(DeploymentErrorCode.INVALID_ARGUMENT, failure.getMessage(), ref, failure);
        } catch (RuntimeException failure) {
            throw DeploymentException.fromRef(DeploymentErrorCode.INTERNAL_ERROR,
                    "Process definition publication validation could not be completed", ref, failure);
        }
        ProcessPreflightReport report = validation.report();
        if (!definition.code().equals(report.getCode())) {
            throw DeploymentException.fromRef(DeploymentErrorCode.INTERNAL_ERROR,
                    "Process definition publication validation returned a report for a different process code", ref);
        }
        if (report.getOverallStatus() != ProcessPreflightReport.OverallStatus.PASS) {
            throw DeploymentException.fromRef(DeploymentErrorCode.INVALID_ARGUMENT, validationFailureMessage(report),
                    ref);
        }
        for (ProcessCallBinding binding : validation.callBindings()) {
            ProcessRef.Version target = binding.target();
            if (!ref.namespace().equals(target.namespace())) {
                throw DeploymentException.fromRef(DeploymentErrorCode.INVALID_ARGUMENT,
                        "Published Process call must inherit the caller namespace", ref);
            }
            ProcessVersionRecord called = repository
                .find(target.namespace(), target.code(), target.version())
                .orElseThrow(() -> DeploymentException.fromRef(DeploymentErrorCode.DEPENDENCY_NOT_FOUND,
                        "Published called-Process version does not exist: " + target, ref));
            if (called.getModelType() != modelType) {
                throw DeploymentException.fromRef(DeploymentErrorCode.INVALID_ARGUMENT,
                        "Published Process call target must use the caller model type", ref);
            }
        }
        return validation.callBindings();
    }
}
