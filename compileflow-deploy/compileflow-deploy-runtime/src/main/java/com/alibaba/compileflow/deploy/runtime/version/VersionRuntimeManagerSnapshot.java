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
package com.alibaba.compileflow.deploy.runtime.version;

import com.alibaba.compileflow.engine.ProcessRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Point-in-time diagnostics for version runtime lifecycle backlog and retention.
 *
 * @author yusu
 */
public final class VersionRuntimeManagerSnapshot {
    private final int inflightCount;
    private final int inflightCapacity;
    private final int inflightAvailablePermits;
    private final long failureBackoffMs;
    private final int retainedRuntimeCount;
    private final List<ProcessRef.Version> inflightVersions;
    private final List<ProcessRef.Version> demandedVersions;
    private final List<ProcessRef.Version> pendingReleaseVersions;
    private final List<BackedOffVersion> backedOffVersions;
    private final List<InstalledProcessSnapshot> installedVersions;

    public VersionRuntimeManagerSnapshot(int inflightCount, int inflightCapacity, int inflightAvailablePermits,
            long failureBackoffMs, int retainedRuntimeCount, List<ProcessRef.Version> inflightVersions,
            List<ProcessRef.Version> demandedVersions, List<ProcessRef.Version> pendingReleaseVersions,
            List<BackedOffVersion> backedOffVersions, List<InstalledProcessSnapshot> installedVersions) {
        this.inflightCount = inflightCount;
        this.inflightCapacity = inflightCapacity;
        this.inflightAvailablePermits = inflightAvailablePermits;
        this.failureBackoffMs = failureBackoffMs;
        this.retainedRuntimeCount = retainedRuntimeCount;
        this.inflightVersions = immutableList(inflightVersions);
        this.demandedVersions = immutableList(demandedVersions);
        this.pendingReleaseVersions = immutableList(pendingReleaseVersions);
        this.backedOffVersions = immutableList(backedOffVersions);
        this.installedVersions = immutableList(installedVersions);
    }

    private static <T> List<T> immutableList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public int getInflightCount() {
        return inflightCount;
    }

    public int getInflightCapacity() {
        return inflightCapacity;
    }

    public int getInflightAvailablePermits() {
        return inflightAvailablePermits;
    }

    public long getFailureBackoffMs() {
        return failureBackoffMs;
    }

    public int getRetainedRuntimeCount() {
        return retainedRuntimeCount;
    }

    public List<ProcessRef.Version> getInflightVersions() {
        return inflightVersions;
    }

    public List<ProcessRef.Version> getDemandedVersions() {
        return demandedVersions;
    }

    public List<ProcessRef.Version> getPendingReleaseVersions() {
        return pendingReleaseVersions;
    }

    public List<BackedOffVersion> getBackedOffVersions() {
        return backedOffVersions;
    }

    public List<InstalledProcessSnapshot> getInstalledVersions() {
        return installedVersions;
    }

    public static final class BackedOffVersion {
        private final ProcessRef.Version key;
        private final String reason;
        private final long blockedUntil;
        private final long remainingMs;

        public BackedOffVersion(ProcessRef.Version key, String reason, long blockedUntil, long remainingMs) {
            this.key = key;
            this.reason = reason;
            this.blockedUntil = blockedUntil;
            this.remainingMs = remainingMs;
        }

        public ProcessRef.Version getKey() {
            return key;
        }

        public String getReason() {
            return reason;
        }

        public long getBlockedUntil() {
            return blockedUntil;
        }

        public long getRemainingMs() {
            return remainingMs;
        }
    }

    public record InstalledProcessSnapshot(String namespace, String code, List<String> versions) {
        public InstalledProcessSnapshot {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(code, "code");
            versions = versions == null || versions.isEmpty()
                    ? Collections.emptyList()
                    : List.copyOf(new TreeSet<>(versions));
        }
    }
}
