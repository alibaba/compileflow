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
package com.alibaba.compileflow.engine.core.runtime;

import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.core.routing.AliasSelection;
import java.util.Objects;

/**
 * Internal request for an existing process reference or an explicit process definition.
 *
 * @author yusu
 */
public final class ProcessRuntimeRequest {
    private final String namespace;
    private final String code;
    private final String version;
    private final String alias;
    private final ProcessDefinition definition;
    private final AliasSelection aliasSelection;
    private final AliasRoutingOptions aliasRouting;

    private ProcessRuntimeRequest(String namespace, String code, String version, String alias,
            ProcessDefinition definition, AliasSelection aliasSelection, AliasRoutingOptions aliasRouting) {
        this.namespace = ProcessIdentifiers.requireNamespace(namespace);
        this.code = ProcessIdentifiers.requireCode(code);
        this.version = version;
        this.alias = alias;
        this.definition = definition;
        this.aliasSelection = aliasSelection;
        this.aliasRouting = Objects.requireNonNull(aliasRouting, "aliasRouting");
    }

    /**
     * Creates a runtime request from an explicit process reference.
     *
     * @param ref version or Alias reference
     * @return internal runtime request
     */
    public static ProcessRuntimeRequest from(ProcessRef ref) {
        ProcessRef reference = Objects.requireNonNull(ref, "ref");
        if (reference instanceof ProcessRef.Version version) {
            return new ProcessRuntimeRequest(version.namespace(), version.code(), version.version(), null, null, null,
                    AliasRoutingOptions.defaults());
        }
        ProcessRef.Alias alias = (ProcessRef.Alias) reference;
        return new ProcessRuntimeRequest(alias.namespace(), alias.code(), null, alias.alias(), null, null,
                AliasRoutingOptions.defaults());
    }

    /**
     * Creates an unversioned standalone definition request.
     *
     * @param definition explicit process definition
     * @return internal runtime request
     */
    public static ProcessRuntimeRequest from(ProcessDefinition definition) {
        ProcessDefinition source = Objects.requireNonNull(definition, "definition");
        return new ProcessRuntimeRequest(ProcessRef.DEFAULT_NAMESPACE, source.code(), null, null, source, null,
                AliasRoutingOptions.defaults());
    }

    /**
     * Creates a request that binds an exact version to exact definition content.
     *
     * @param version    exact version reference
     * @param definition exact process definition
     * @return versioned definition request
     */
    public static ProcessRuntimeRequest versioned(ProcessRef.Version version, ProcessDefinition definition) {
        ProcessRef.Version ref = Objects.requireNonNull(version, "version");
        ProcessDefinition source = Objects.requireNonNull(definition, "definition");
        if (!ref.code().equals(source.code())) {
            throw new IllegalArgumentException("Version reference code and definition code must match");
        }
        return new ProcessRuntimeRequest(ref.namespace(), ref.code(), ref.version(), null, source, null,
                AliasRoutingOptions.defaults());
    }

    /**
     * Creates an identity-only request for a node in an admitted Process-call graph.
     */
    public static ProcessRuntimeRequest forProcessIdentity(String namespace, String code, ProcessRef.Version version) {
        if (version == null) {
            return new ProcessRuntimeRequest(namespace, code, null, null, null, null, AliasRoutingOptions.defaults());
        }
        if (!version.namespace().equals(namespace) || !version.code().equals(code)) {
            throw new IllegalArgumentException("Version must identify the same process");
        }
        return from(version);
    }

    /**
     * Creates a request for one already-selected Alias target.
     *
     * @param alias originally requested Alias
     * @param selection selected version and observed route attribution
     * @return exact version request that retains the original Alias identity
     */
    public static ProcessRuntimeRequest forAliasSelection(ProcessRef.Alias alias, AliasSelection selection) {
        ProcessRef.Alias requestedAlias = Objects.requireNonNull(alias, "alias");
        AliasSelection admitted = Objects.requireNonNull(selection, "selection");
        ProcessRef.Version effective = admitted.version();
        if (!requestedAlias.namespace().equals(effective.namespace()) || !requestedAlias
            .code()
            .equals(effective.code())) {
            throw new IllegalArgumentException("Alias selection must identify the requested process");
        }
        return new ProcessRuntimeRequest(effective.namespace(), effective.code(), effective.version(),
                requestedAlias.alias(), null, admitted, AliasRoutingOptions.defaults());
    }

    /**
     * Returns a copy with the effective selected version.
     *
     * @param effectiveVersion exact selected version
     * @return version-bound request
     */
    public ProcessRuntimeRequest withVersion(String effectiveVersion) {
        ProcessRef.Version ref = ProcessRef.version(namespace, code, effectiveVersion);
        return new ProcessRuntimeRequest(ref.namespace(), ref.code(), ref.version(), null, definition, null,
                AliasRoutingOptions.defaults());
    }

    /**
     * Attaches inputs that are consumed only while resolving an Alias root.
     */
    public ProcessRuntimeRequest withAliasRouting(AliasRoutingOptions options) {
        Objects.requireNonNull(options, "options");
        if (alias == null && !options.isEmpty()) {
            throw new IllegalArgumentException("Alias routing options require a ProcessRef.Alias root");
        }
        return new ProcessRuntimeRequest(namespace, code, version, alias, definition, aliasSelection, options);
    }

    /**
     * Returns the process namespace.
     *
     * @return logical process namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the process code.
     *
     * @return validated process code unchanged
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the exact requested or selected version.
     *
     * @return version, or {@code null}
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the exact requested alias.
     *
     * @return alias, or {@code null}
     */
    public String getAlias() {
        return alias;
    }

    /**
     * Returns the explicit definition.
     *
     * @return definition, or {@code null} for reference-only requests
     */
    public ProcessDefinition getDefinition() {
        return definition;
    }

    /**
     * Returns the preselected Alias result.
     *
     * @return admitted selection, or {@code null} for direct engine routing
     */
    public AliasSelection getAliasSelection() {
        return aliasSelection;
    }

    public AliasRoutingOptions getAliasRouting() {
        return aliasRouting;
    }

    /**
     * Returns whether exact definition content is available for compilation.
     *
     * @return {@code true} when a definition is present
     */
    public boolean hasDefinition() {
        return definition != null;
    }

    @Override
    public String toString() {
        return "ProcessRuntimeRequest{namespace='" + namespace + "', code='" + code + "', version='" + version
                + "', alias='" + alias + "', definition=" + definition + '}';
    }
}
