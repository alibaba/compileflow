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
package com.alibaba.compileflow.deploy.testkit;

import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionRecord;
import com.alibaba.compileflow.deploy.spi.store.ProcessVersionStore;
import com.alibaba.compileflow.deploy.spi.store.PublishedVersionPageKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Deterministic in-memory version Store with immutable publication semantics.
 *
 * @author yusu
 */
public final class InMemoryProcessVersionStore implements ProcessVersionStore {
    private final ConcurrentMap<String, Ledger> ledgers = new ConcurrentHashMap<>();

    private static DeploymentException versionConflict(ProcessVersionRecord candidate) {
        return DeploymentException
            .builder(DeploymentErrorCode.VERSION_CONFLICT,
                    "Process version already exists with a different executable artifact")
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
            PublishedVersionPageKey cursor, int limit) {
        if (limit <= 0 || limit > 101) {
            throw new IllegalArgumentException("limit must be between 1 and 101");
        }
        Ledger ledger = ledgers.get(flowKey(namespace, code));
        if (ledger == null) {
            return Collections.emptyList();
        }
        synchronized (ledger) {
            List<ProcessVersionRecord> records = new ArrayList<>();
            for (ProcessVersionRecord record : ledger.versions.values()) {
                if (versionPrefix == null || record.getVersion().startsWith(versionPrefix)) {
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
            return List.copyOf(records.subList(0, toIndex));
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
            if (existing.getArtifactDigest().equals(candidate.getArtifactDigest())) {
                return existing;
            }
            throw versionConflict(candidate);
        }
    }

    private String flowKey(String namespace, String code) {
        ProcessRef.Version ref = ProcessRef.version(namespace, code, "key");
        return ref.namespace() + "\u001f" + ref.code();
    }

    private static final class Ledger {
        private final Map<String, ProcessVersionRecord> versions = new HashMap<>();
    }
}
