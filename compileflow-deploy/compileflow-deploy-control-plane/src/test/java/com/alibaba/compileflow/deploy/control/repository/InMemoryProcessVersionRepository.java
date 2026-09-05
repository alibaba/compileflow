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
package com.alibaba.compileflow.deploy.control.repository;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.commons.lang3.StringUtils;

/**
 * Deterministic in-memory test repository with the same publication semantics as JDBC.
 *
 * @author yusu
 */
public final class InMemoryProcessVersionRepository implements ProcessVersionRepository {
    private final ConcurrentMap<String, Ledger> ledgers = new ConcurrentHashMap<>();

    private static boolean sameContent(ProcessVersionRecord existing, ProcessVersionRecord candidate) {
        return existing.getModelType() == candidate.getModelType()
                && existing.getProcessDefinition().equals(candidate.getProcessDefinition())
                && existing.getArtifactDigest().equals(candidate.getArtifactDigest());
    }

    private static DeploymentException versionConflict(ProcessVersionRecord candidate) {
        return DeploymentException
            .builder(DeploymentErrorCode.VERSION_CONFLICT,
                    "Process version already exists with different content or model type")
            .namespace(candidate.getNamespace())
            .code(candidate.getCode())
            .version(candidate.getVersion())
            .build();
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public Optional<ProcessVersionRecord> find(String namespace, String code, String version) {
        ProcessRef.Version ref = ProcessRef.version(namespace, code, version);
        Ledger ledger = ledgers.get(flowKey(ref.namespace(), ref.code()));
        if (ledger == null) {
            return Optional.empty();
        }
        synchronized (ledger) {
            return Optional.ofNullable(ledger.versions.get(ref.version()));
        }
    }

    @Override
    public List<ProcessVersionRecord> list(String namespace, String code, String versionPrefix,
            DeployCursorCodec.PublishedVersionKey cursor, int limit) {
        if (limit <= 0 || limit > 101) {
            throw new IllegalArgumentException("limit must be between 1 and 101");
        }
        Ledger ledger = ledgers.get(flowKey(namespace, code));
        if (ledger == null) {
            return Collections.emptyList();
        }
        synchronized (ledger) {
            String prefix = normalizedPrefix(versionPrefix);
            List<ProcessVersionRecord> records = new ArrayList<>();
            for (ProcessVersionRecord record : ledger.versions.values()) {
                if (prefix == null || record.getVersion().startsWith(prefix)) {
                    records.add(record);
                }
            }
            records.sort(Comparator
                .comparingLong(ProcessVersionRecord::getCreatedAt)
                .reversed()
                .thenComparing(ProcessVersionRecord::getVersion, Comparator.reverseOrder()));
            if (cursor != null) {
                records.removeIf(record -> record.getCreatedAt() > cursor.createdAt()
                        || (record.getCreatedAt() == cursor.createdAt()
                        && record.getVersion().compareTo(cursor.version()) >= 0));
            }
            int toIndex = Math.min(limit, records.size());
            return Collections.unmodifiableList(new ArrayList<>(records.subList(0, toIndex)));
        }
    }

    @Override
    public ProcessVersionRecord save(ProcessVersionRecord record) {
        ProcessVersionRecord candidate = Objects.requireNonNull(record, "record");
        Ledger ledger =
                ledgers.computeIfAbsent(flowKey(candidate.getNamespace(), candidate.getCode()), ignored -> new Ledger());
        synchronized (ledger) {
            ProcessVersionRecord existing = ledger.versions.get(candidate.getVersion());
            if (existing == null) {
                ledger.versions.put(candidate.getVersion(), candidate);
                return candidate;
            }
            if (sameContent(existing, candidate)) {
                return existing;
            }
            throw versionConflict(candidate);
        }
    }

    private String flowKey(String namespace, String code) {
        ProcessRef.Version ref = ProcessRef.version(namespace, code, "key");
        return ref.namespace() + "\u001f" + ref.code();
    }

    private String normalizedPrefix(String versionPrefix) {
        return StringUtils.trimToNull(versionPrefix);
    }

    private static final class Ledger {
        private final ConcurrentMap<String, ProcessVersionRecord> versions = new ConcurrentHashMap<>();
    }
}
