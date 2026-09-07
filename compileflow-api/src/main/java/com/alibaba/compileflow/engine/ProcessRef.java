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
package com.alibaba.compileflow.engine;

/**
 * Identifies an exact process coordinate or a published Alias without carrying definition content.
 *
 * <p>{@link Version} selects one exact immutable revision. The coordinate may be bound locally by
 * {@link ProcessRuntimeManager} or supplied by a deployment control plane. {@link Alias} selects
 * through a published Alias route. Definition sources are represented by {@link ProcessDefinition}.
 *
 * @author yusu
 */
public sealed interface ProcessRef permits ProcessRef.Version, ProcessRef.Alias {
    /**
     * Namespace used when a caller does not provide one.
     */
    String DEFAULT_NAMESPACE = "default";

    /**
     * Creates an exact version reference in the default process namespace.
     *
     * @param code    process code
     * @param version immutable exact version
     * @return version reference
     */
    static Version version(String code, String version) {
        return new Version(DEFAULT_NAMESPACE, code, version);
    }

    /**
     * Creates an exact version reference.
     *
     * @param namespace process namespace
     * @param code      process code
     * @param version   immutable exact version
     * @return version reference
     */
    static Version version(String namespace, String code, String version) {
        return new Version(namespace, code, version);
    }

    /**
     * Creates an alias reference in the default process namespace.
     *
     * @param code  process code
     * @param alias published alias
     * @return alias reference
     */
    static Alias alias(String code, String alias) {
        return new Alias(DEFAULT_NAMESPACE, code, alias);
    }

    /**
     * Creates an alias reference.
     *
     * @param namespace process namespace
     * @param code      process code
     * @param alias     published alias
     * @return alias reference
     */
    static Alias alias(String namespace, String code, String alias) {
        return new Alias(namespace, code, alias);
    }

    /**
     * Returns the logical process namespace.
     *
     * @return validated process namespace unchanged
     */
    String namespace();

    /**
     * Returns the process code.
     *
     * @return validated process code unchanged
     */
    String code();

    /**
     * References one exact immutable version coordinate.
     *
     * @param namespace process namespace
     * @param code      process code
     * @param version   immutable exact version
     */
    record Version(String namespace, String code, String version) implements ProcessRef {
        /**
         * Validates the reference without normalizing its identities.
         *
         * @param namespace explicit process namespace
         * @param code      process code
         * @param version   immutable exact version
         */
        public Version {
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            code = ProcessIdentifiers.requireCode(code);
            version = ProcessIdentifiers.requireVersion(version);
        }
    }

    /**
     * References one published alias.
     *
     * @param namespace process namespace
     * @param code      process code
     * @param alias     published alias
     */
    record Alias(String namespace, String code, String alias) implements ProcessRef {
        /**
         * Validates the reference without normalizing its identities.
         *
         * @param namespace explicit process namespace
         * @param code      process code
         * @param alias     published alias
         */
        public Alias {
            namespace = ProcessIdentifiers.requireNamespace(namespace);
            code = ProcessIdentifiers.requireCode(code);
            alias = ProcessIdentifiers.requireAlias(alias);
        }
    }
}
