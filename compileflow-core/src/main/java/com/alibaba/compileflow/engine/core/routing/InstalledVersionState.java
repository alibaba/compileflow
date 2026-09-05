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

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessRef;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable node-local state of installed process runtimes.
 *
 * @author yusu
 */
public final class InstalledVersionState {
    /*
     * Installation changes are control-plane operations while contains() is on the execution
     * path. Immutable copy-on-write sets keep reads lock-free and make every returned value a
     * real snapshot rather than a live view of a concurrently mutating set.
     */
    private final ConcurrentHashMap<Key, Set<String>> installedVersions = new ConcurrentHashMap<>();
    private final boolean localInstallationRequired;

    public InstalledVersionState() {
        this(false);
    }

    public InstalledVersionState(boolean localInstallationRequired) {
        this.localInstallationRequired = localInstallationRequired;
    }

    public boolean hasAny(String namespace, String code) {
        Set<String> set = installedVersions.get(new Key(namespace, code));
        return set != null && !set.isEmpty();
    }

    public boolean isEmpty() {
        return installedVersions.isEmpty();
    }

    public boolean isLocalInstallationRequired() {
        return localInstallationRequired;
    }

    public boolean contains(String namespace, String code, String version) {
        ProcessRef.Version ref = ProcessRef.version(namespace, code, version);
        Set<String> set = installedVersions.get(new Key(ref.namespace(), ref.code()));
        return set != null && set.contains(ref.version());
    }

    public Optional<Set<String>> list(String namespace, String code) {
        Set<String> set = installedVersions.get(new Key(namespace, code));
        return Optional.ofNullable(set);
    }

    public List<InstalledProcess> snapshot() {
        return installedVersions
            .entrySet()
            .stream()
            .map(entry -> new InstalledProcess(entry.getKey().namespace(), entry.getKey().code(), entry.getValue()))
            .sorted((left, right) -> {
                int namespaceOrder = left.namespace().compareTo(right.namespace());
                return namespaceOrder != 0 ? namespaceOrder : left.code().compareTo(right.code());
            })
            .toList();
    }

    public void markInstalled(String namespace, String code, String version) {
        ProcessRef.Version ref = ProcessRef.version(namespace, code, version);
        Key key = new Key(ref.namespace(), ref.code());
        Set<String> observed = installedVersions.get(key);
        if (observed != null && observed.contains(ref.version())) {
            return;
        }
        installedVersions.compute(key, (ignored, current) -> {
            if (current != null && current.contains(ref.version())) {
                return current;
            }
            Set<String> updated = current == null ? new LinkedHashSet<>() : new LinkedHashSet<>(current);
            updated.add(ref.version());
            return Set.copyOf(updated);
        });
    }

    public boolean markUninstalled(String namespace, String code, String version) {
        ProcessRef.Version ref = ProcessRef.version(namespace, code, version);
        boolean[] removed = {false};
        installedVersions.computeIfPresent(new Key(ref.namespace(), ref.code()), (ignored, current) -> {
            if (!current.contains(ref.version())) {
                return current;
            }
            removed[0] = true;
            if (current.size() == 1) {
                return null;
            }
            Set<String> updated = new LinkedHashSet<>(current);
            updated.remove(ref.version());
            return Set.copyOf(updated);
        });
        return removed[0];
    }

    public Set<String> removeAll(String namespace, String code) {
        Set<String> removed = installedVersions.remove(new Key(namespace, code));
        return removed == null ? Collections.emptySet() : removed;
    }

    public void clear() {
        installedVersions.clear();
    }

    private record Key(String namespace, String code) {
        private Key {
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            code = ProcessIdentifiers.requireCode(code);
        }
    }

    /**
     * Typed immutable diagnostic view of locally installed versions for one process.
     *
     * @param namespace process namespace
     * @param code      process code
     * @param versions  sorted immutable version set
     */
    public record InstalledProcess(String namespace, String code, Set<String> versions) {
        /**
         * Validates identity and freezes the version set.
         *
         * @param namespace process namespace
         * @param code      process code
         * @param versions  installed versions
         */
        public InstalledProcess {
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            code = ProcessIdentifiers.requireCode(code);
            versions = Collections.unmodifiableSet(new TreeSet<>(Objects.requireNonNull(versions, "versions")));
        }
    }
}
