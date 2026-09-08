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
package com.alibaba.compileflow.engine.core.runtime.cache;

import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeEntry;
import com.alibaba.compileflow.engine.core.runtime.ProcessRuntimeIdentity;
import com.alibaba.compileflow.engine.core.semantic.SemanticText;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default concurrent cache of compiled process runtimes.
 *
 * @author yusu
 */
public class DefaultProcessRuntimeCache implements ProcessRuntimeCache {
    private final Object mutationLock = new Object();
    private final int maxResidentRuntimes;
    private final Cache<ProcessRuntimeIdentity, ProcessRuntimeEntry> runtimes;
    private final Map<String, ProcessRuntimeIdentity> activeBindings = new ConcurrentHashMap<>();
    private final Map<String, ImmutableProcessBinding> bindingGuards = new HashMap<>();
    private final Map<String, Set<ProcessRuntimeIdentity>> bindingRuntimes = new HashMap<>();
    private final Map<ProcessRuntimeIdentity, Set<String>> runtimeBindings = new HashMap<>();
    private final Map<Retention, ProcessRuntimeIdentity> retainedBindings = new HashMap<>();
    private final Map<String, Set<Retention>> bindingRetainers = new HashMap<>();
    private final Map<ProcessRuntimeIdentity, ProcessRuntimeEntry> retainedRuntimes = new ConcurrentHashMap<>();
    private final Map<ProcessRuntimeIdentity, Set<Retention>> runtimeRetainers = new HashMap<>();

    public DefaultProcessRuntimeCache(int maxResidentRuntimes) {
        if (maxResidentRuntimes <= 0) {
            throw new IllegalArgumentException("maxResidentRuntimes must be positive");
        }
        this.maxResidentRuntimes = maxResidentRuntimes;
        this.runtimes = CacheBuilder
            .newBuilder()
            .maximumSize(maxResidentRuntimes)
            .removalListener(this::onRuntimeRemoved)
            .build();
    }

    @Override
    public ProcessRuntimeEntry getIfPresent(String bindingKey) {
        Objects.requireNonNull(bindingKey, "bindingKey");
        ProcessRuntimeIdentity runtimeIdentity = activeBindings.get(bindingKey);
        if (runtimeIdentity == null) {
            return null;
        }
        ProcessRuntimeEntry runtime = getRuntime(runtimeIdentity);
        if (runtime == null) {
            synchronized (mutationLock) {
                if (getRuntime(runtimeIdentity) == null) {
                    removeRuntimeMappings(runtimeIdentity);
                }
            }
        }
        return runtime;
    }

    @Override
    public ProcessRuntimeEntry getIfPresent(ProcessRuntimeIdentity runtimeIdentity) {
        return getRuntime(Objects.requireNonNull(runtimeIdentity, "runtimeIdentity"));
    }

    @Override
    public ProcessRuntimeEntry cacheExact(ProcessRuntimeEntry runtime) {
        ProcessRuntimeEntry candidate = Objects.requireNonNull(runtime, "runtime");
        ProcessRuntimeIdentity candidateIdentity = candidate.getRuntimeIdentity();
        synchronized (mutationLock) {
            ProcessRuntimeEntry current = getRuntime(candidateIdentity);
            if (current != null) {
                return current;
            }
            ensureCapacityFor(Set.of(candidateIdentity));
            runtimes.put(candidateIdentity, candidate);
            return candidate;
        }
    }

    @Override
    public boolean conflictsWithImmutableBinding(String bindingKey, ProcessRuntimeIdentity runtimeIdentity) {
        Objects.requireNonNull(bindingKey, "bindingKey");
        Objects.requireNonNull(runtimeIdentity, "runtimeIdentity");
        synchronized (mutationLock) {
            ImmutableProcessBinding guard = bindingGuards.get(bindingKey);
            return guard != null && !guard.equals(ImmutableProcessBinding.from(runtimeIdentity));
        }
    }

    @Override
    public InstallResult install(String bindingKey, ProcessRuntimeEntry expectedEntry, ProcessRuntimeEntry newEntry) {
        Objects.requireNonNull(bindingKey, "bindingKey");
        ProcessRuntimeEntry candidate = Objects.requireNonNull(newEntry, "newEntry");
        ProcessRuntimeIdentity candidateIdentity = candidate.getRuntimeIdentity();
        synchronized (mutationLock) {
            ImmutableProcessBinding candidateGuard = ImmutableProcessBinding.from(candidateIdentity);
            ImmutableProcessBinding currentGuard = bindingGuards.get(bindingKey);
            if (currentGuard != null && !currentGuard.equals(candidateGuard)) {
                return InstallResult.VERSION_CONFLICT;
            }
            ProcessRuntimeIdentity currentIdentity = activeBindings.get(bindingKey);
            ProcessRuntimeEntry current = currentIdentity == null ? null : getRuntime(currentIdentity);
            if (currentIdentity != null && current == null) {
                removeRuntimeMappings(currentIdentity);
                currentIdentity = null;
            }
            if (current != expectedEntry) {
                if (candidateIdentity.equals(currentIdentity)) {
                    return InstallResult.ALREADY_INSTALLED;
                }
                return InstallResult.VERSION_CONFLICT;
            }

            ProcessRuntimeEntry canonical = getRuntime(candidateIdentity);
            if (canonical == null) {
                ensureCapacityFor(Set.of(candidateIdentity));
                canonical = candidate;
                runtimes.put(candidateIdentity, canonical);
            }
            activeBindings.put(bindingKey, candidateIdentity);
            bindingGuards.putIfAbsent(bindingKey, candidateGuard);
            addRuntimeBinding(bindingKey, candidateIdentity);
            return currentIdentity == null || !candidateIdentity.equals(currentIdentity)
                    ? InstallResult.INSTALLED
                    : InstallResult.ALREADY_INSTALLED;
        }
    }

    @Override
    public InstallResult installAndRetainBatch(List<Installation> installations, String ownerId) {
        List<Installation> requested = List.copyOf(Objects.requireNonNull(installations, "installations"));
        String owner = SemanticText.requireIdentity(ownerId, "ownerId");
        if (requested.isEmpty()) {
            return InstallResult.ALREADY_INSTALLED;
        }
        synchronized (mutationLock) {
            Set<String> bindingKeys = new HashSet<>();
            List<ValidatedInstallation> validated = new ArrayList<>(requested.size());
            boolean changed = false;
            for (Installation installation : requested) {
                if (!bindingKeys.add(installation.bindingKey())) {
                    throw new IllegalArgumentException("Duplicate batch bindingKey: " + installation.bindingKey());
                }
                InstallResult validation = validateInstallation(installation);
                if (validation == InstallResult.VERSION_CONFLICT) {
                    return validation;
                }
                ProcessRuntimeIdentity currentIdentity = activeRuntimeIdentity(installation.bindingKey());
                validated.add(new ValidatedInstallation(installation, currentIdentity));
                changed |= validation == InstallResult.INSTALLED;
            }
            ensureRetainedBatchCapacity(validated);

            for (ValidatedInstallation item : validated) {
                Installation installation = item.installation();
                ProcessRuntimeEntry candidate = installation.newEntry();
                ProcessRuntimeIdentity candidateIdentity = candidate.getRuntimeIdentity();
                ProcessRuntimeIdentity currentIdentity = item.currentIdentity();
                if (currentIdentity == null && activeBindings.containsKey(installation.bindingKey())) {
                    ProcessRuntimeIdentity staleIdentity = activeBindings.get(installation.bindingKey());
                    removeRuntimeMappings(staleIdentity);
                }
                ProcessRuntimeEntry canonical = getRuntime(candidateIdentity);
                if (canonical == null) {
                    canonical = candidate;
                    runtimes.put(candidateIdentity, canonical);
                }
                activeBindings.put(installation.bindingKey(), candidateIdentity);
                bindingGuards.putIfAbsent(installation.bindingKey(), ImmutableProcessBinding.from(candidateIdentity));
                addRuntimeBinding(installation.bindingKey(), candidateIdentity);
                retain(installation.bindingKey(), owner);
            }
            return changed ? InstallResult.INSTALLED : InstallResult.ALREADY_INSTALLED;
        }
    }

    @Override
    public boolean retain(String bindingKey, String ownerId) {
        Retention retention = new Retention(bindingKey, ownerId);
        synchronized (mutationLock) {
            ProcessRuntimeIdentity runtimeIdentity = activeBindings.get(bindingKey);
            ProcessRuntimeEntry runtime = runtimeIdentity == null ? null : getRuntime(runtimeIdentity);
            if (runtime == null) {
                return false;
            }

            ProcessRuntimeIdentity previous = retainedBindings.put(retention, runtimeIdentity);
            if (runtimeIdentity.equals(previous)) {
                return true;
            }
            if (previous != null) {
                removeRetainer(retention, previous);
            }
            retainedRuntimes.put(runtimeIdentity, runtime);
            bindingRetainers
                .computeIfAbsent(bindingKey, ignored -> new HashSet<>())
                .add(retention);
            runtimeRetainers
                .computeIfAbsent(runtimeIdentity, ignored -> new HashSet<>())
                .add(retention);
            return true;
        }
    }

    private InstallResult validateInstallation(Installation installation) {
        String bindingKey = installation.bindingKey();
        ProcessRuntimeEntry candidate = installation.newEntry();
        ProcessRuntimeIdentity candidateIdentity = candidate.getRuntimeIdentity();
        ImmutableProcessBinding currentGuard = bindingGuards.get(bindingKey);
        if (currentGuard != null && !currentGuard.equals(ImmutableProcessBinding.from(candidateIdentity))) {
            return InstallResult.VERSION_CONFLICT;
        }
        ProcessRuntimeIdentity currentIdentity = activeRuntimeIdentity(bindingKey);
        ProcessRuntimeEntry current = currentIdentity == null ? null : getRuntime(currentIdentity);
        if (current != installation.expectedEntry()) {
            if (candidateIdentity.equals(currentIdentity)) {
                return InstallResult.ALREADY_INSTALLED;
            }
            return InstallResult.VERSION_CONFLICT;
        }
        return currentIdentity == null || !candidateIdentity.equals(currentIdentity)
                ? InstallResult.INSTALLED
                : InstallResult.ALREADY_INSTALLED;
    }

    private ProcessRuntimeIdentity activeRuntimeIdentity(String bindingKey) {
        ProcessRuntimeIdentity runtimeIdentity = activeBindings.get(bindingKey);
        return runtimeIdentity != null && getRuntime(runtimeIdentity) != null ? runtimeIdentity : null;
    }

    private void ensureRetainedBatchCapacity(List<ValidatedInstallation> installations) {
        Set<ProcessRuntimeIdentity> candidates = installations
            .stream()
            .map(item -> item.installation().newEntry().getRuntimeIdentity())
            .collect(java.util.stream.Collectors.toSet());
        long newlyRetained = candidates
            .stream()
            .filter(candidate -> !retainedRuntimes.containsKey(candidate))
            .count();
        if (retainedRuntimes.size() + newlyRetained > maxResidentRuntimes) {
            throw capacityExceeded();
        }
        ensureCapacityFor(candidates);
    }

    private void ensureCapacityFor(Set<ProcessRuntimeIdentity> candidates) {
        long missing = candidates
            .stream()
            .filter(candidate -> getRuntime(candidate) == null)
            .count();
        while (residentRuntimeCount() + missing > maxResidentRuntimes) {
            ProcessRuntimeIdentity evictable = runtimes
                .asMap()
                .keySet()
                .stream()
                .filter(runtime -> !retainedRuntimes.containsKey(runtime))
                .filter(runtime -> !candidates.contains(runtime))
                .findFirst()
                .orElseThrow(this::capacityExceeded);
            runtimes.invalidate(evictable);
            runtimes.cleanUp();
        }
    }

    private long residentRuntimeCount() {
        Set<ProcessRuntimeIdentity> identities = new HashSet<>(runtimes.asMap().keySet());
        identities.addAll(retainedRuntimes.keySet());
        return identities.size();
    }

    private CompileFlowException capacityExceeded() {
        return new CompileFlowException(ErrorCode.CF_RUNTIME_001, "Process runtime resident capacity is exhausted")
            .withContext("cause", "CAPACITY")
            .withContext("maxResidentRuntimes", maxResidentRuntimes)
            .withContext("residentRuntimes", residentRuntimeCount())
            .withContext("retainedRuntimes", retainedRuntimes.size());
    }

    @Override
    public ReleaseResult release(String bindingKey, String ownerId) {
        Retention retention = new Retention(bindingKey, ownerId);
        synchronized (mutationLock) {
            ProcessRuntimeIdentity retained = retainedBindings.remove(retention);
            if (retained == null) {
                return ReleaseResult.NOT_RETAINED;
            }
            removeRetainer(retention, retained);
            if (bindingRetainers.containsKey(bindingKey)) {
                return ReleaseResult.RETAINED;
            }
            invalidateBinding(bindingKey);
            return ReleaseResult.INVALIDATED;
        }
    }

    @Override
    public boolean invalidateIfUnretained(String bindingKey) {
        Objects.requireNonNull(bindingKey, "bindingKey");
        synchronized (mutationLock) {
            if (bindingRetainers.containsKey(bindingKey)) {
                return false;
            }
            invalidateBinding(bindingKey);
            return true;
        }
    }

    @Override
    public void invalidate(String bindingKey) {
        Objects.requireNonNull(bindingKey, "bindingKey");
        synchronized (mutationLock) {
            Set<Retention> retainers = new HashSet<>(bindingRetainers.getOrDefault(bindingKey, Collections.emptySet()));
            for (Retention retention : retainers) {
                ProcessRuntimeIdentity retained = retainedBindings.remove(retention);
                if (retained != null) {
                    removeRetainer(retention, retained);
                }
            }
            invalidateBinding(bindingKey);
        }
    }

    @Override
    public void invalidateAll() {
        synchronized (mutationLock) {
            activeBindings.clear();
            bindingGuards.clear();
            bindingRuntimes.clear();
            runtimeBindings.clear();
            retainedBindings.clear();
            bindingRetainers.clear();
            retainedRuntimes.clear();
            runtimeRetainers.clear();
            runtimes.invalidateAll();
        }
    }

    @Override
    public long size() {
        synchronized (mutationLock) {
            return residentRuntimeCount();
        }
    }

    private void addRuntimeBinding(String bindingKey, ProcessRuntimeIdentity runtimeIdentity) {
        bindingRuntimes
            .computeIfAbsent(bindingKey, ignored -> new HashSet<>())
            .add(runtimeIdentity);
        runtimeBindings
            .computeIfAbsent(runtimeIdentity, ignored -> new HashSet<>())
            .add(bindingKey);
    }

    private void onRuntimeRemoved(RemovalNotification<ProcessRuntimeIdentity, ProcessRuntimeEntry> notification) {
        ProcessRuntimeIdentity runtimeIdentity = notification.getKey();
        if (runtimeIdentity == null) {
            return;
        }
        synchronized (mutationLock) {
            if (retainedRuntimes.containsKey(runtimeIdentity)) {
                return;
            }
            removeRuntimeMappings(runtimeIdentity);
        }
    }

    private ProcessRuntimeEntry getRuntime(ProcessRuntimeIdentity runtimeIdentity) {
        ProcessRuntimeEntry retained = retainedRuntimes.get(runtimeIdentity);
        return retained == null ? runtimes.getIfPresent(runtimeIdentity) : retained;
    }

    private void invalidateBinding(String bindingKey) {
        activeBindings.remove(bindingKey);
        bindingGuards.remove(bindingKey);
        Set<ProcessRuntimeIdentity> ownedRuntimes = bindingRuntimes.remove(bindingKey);
        if (ownedRuntimes == null) {
            return;
        }
        for (ProcessRuntimeIdentity runtimeIdentity : ownedRuntimes) {
            Set<String> owners = runtimeBindings.get(runtimeIdentity);
            if (owners == null) {
                continue;
            }
            owners.remove(bindingKey);
            if (owners.isEmpty()) {
                runtimeBindings.remove(runtimeIdentity);
                runtimes.invalidate(runtimeIdentity);
            }
        }
    }

    private void removeRetainer(Retention retention, ProcessRuntimeIdentity runtimeIdentity) {
        Set<Retention> bindingOwners = bindingRetainers.get(retention.bindingKey());
        if (bindingOwners != null) {
            bindingOwners.remove(retention);
            if (bindingOwners.isEmpty()) {
                bindingRetainers.remove(retention.bindingKey());
            }
        }
        Set<Retention> retainers = runtimeRetainers.get(runtimeIdentity);
        if (retainers == null) {
            return;
        }
        retainers.remove(retention);
        if (!retainers.isEmpty()) {
            return;
        }
        runtimeRetainers.remove(runtimeIdentity);
        retainedRuntimes.remove(runtimeIdentity);
        if (runtimes.getIfPresent(runtimeIdentity) == null) {
            removeRuntimeMappings(runtimeIdentity);
        }
    }

    private void removeRuntimeMappings(ProcessRuntimeIdentity runtimeIdentity) {
        Set<String> bindings = runtimeBindings.remove(runtimeIdentity);
        if (bindings == null) {
            return;
        }
        for (String bindingKey : bindings) {
            activeBindings.computeIfPresent(bindingKey, (key, activeIdentity) -> activeIdentity.equals(runtimeIdentity)
                    ? null
                    : activeIdentity);
            Set<ProcessRuntimeIdentity> ownedRuntimes = bindingRuntimes.get(bindingKey);
            if (ownedRuntimes != null) {
                ownedRuntimes.remove(runtimeIdentity);
                if (ownedRuntimes.isEmpty()) {
                    bindingRuntimes.remove(bindingKey);
                    bindingGuards.remove(bindingKey);
                }
            }
        }
    }

    int bindingGuardSize() {
        synchronized (mutationLock) {
            return bindingGuards.size();
        }
    }

    private record Retention(String bindingKey, String ownerId) {
        private Retention {
            bindingKey = SemanticText.requireIdentity(bindingKey, "bindingKey");
            ownerId = SemanticText.requireIdentity(ownerId, "ownerId");
        }
    }

    private record ValidatedInstallation(Installation installation, ProcessRuntimeIdentity currentIdentity) {}

    private record ImmutableProcessBinding(ProcessModelType modelType, String code, String sourceDigest) {
        private static ImmutableProcessBinding from(ProcessRuntimeIdentity runtimeIdentity) {
            return new ImmutableProcessBinding(runtimeIdentity.getModelType(), runtimeIdentity.getCode(),
                    runtimeIdentity.getSourceDigest());
        }
    }
}
