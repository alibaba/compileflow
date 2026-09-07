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
package com.alibaba.compileflow.engine.spi.execution;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable identity of a synchronous {@link com.alibaba.compileflow.engine.ProcessEngine}
 * action attempt running on the current thread.
 *
 * <p>The invocation key identifies one logical action invocation within one
 * exact process artifact and remains stable across all of its retry attempts.
 * Applications can use it as an idempotency key when calling an external
 * system. Replaying the same process invocation against different source
 * content intentionally produces a different key. The attempt number starts
 * at one and changes for every retry.
 *
 * <p>This context belongs to ProcessEngine invocation-policy execution. Durable
 * Actions and Effect workers do not install it; Durable identity is supplied by
 * their persistent Run and Effect protocols. The context is thread-confined.
 * CompileFlow propagates it only across engine-owned synchronous Action boundaries and
 * never into asynchronous tasks created by application code. An application that deliberately
 * hands work to another executor must pass the values it needs explicitly; this type is not a
 * general-purpose context carrier.
 *
 * @author yusu
 */
public final class ActionExecutionContext {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private final String processInvocationId;
    private final String namespace;
    private final String processCode;
    private final ProcessModelType modelType;
    private final String sourceDigest;
    private final String nodeId;
    private final long invocationOrdinal;
    private final int attemptNumber;
    private final String invocationKey;

    /**
     * Creates an action attempt context.
     *
     * @param processInvocationId process invocation identifier
     * @param namespace           process namespace
     * @param processCode         process code
     * @param modelType           process model type
     * @param sourceDigest        digest of the exact process source being executed
     * @param nodeId              process node identifier
     * @param invocationOrdinal   one-based occurrence number for this node
     * @param attemptNumber       one-based retry attempt number
     */
    public ActionExecutionContext(String processInvocationId, String namespace, String processCode,
            ProcessModelType modelType, String sourceDigest, String nodeId, long invocationOrdinal, int attemptNumber) {
        this.processInvocationId = ProcessIdentifiers.requireInvocationId(processInvocationId);
        this.namespace = ProcessIdentifiers.requireNamespace(namespace);
        this.processCode = ProcessIdentifiers.requireCode(processCode);
        this.modelType = Objects.requireNonNull(modelType, "modelType");
        this.sourceDigest = ProcessIdentifiers.requireSha256(sourceDigest, "sourceDigest");
        this.nodeId = ProcessIdentifiers.requireNodeId(nodeId);
        if (invocationOrdinal <= 0L) {
            throw new IllegalArgumentException("invocationOrdinal must be positive");
        }
        this.invocationOrdinal = invocationOrdinal;
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        this.attemptNumber = attemptNumber;
        this.invocationKey = createInvocationKey();
    }

    /**
     * Returns the context bound to the current action thread.
     *
     * @return current action execution context
     * @throws IllegalStateException when called outside an action invocation
     */
    public static ActionExecutionContext current() {
        ActionExecutionContext context = currentOptional().orElse(null);
        if (context == null) {
            throw new IllegalStateException("No action execution context is available on the current thread");
        }
        return context;
    }

    /**
     * Returns the context bound to the current thread, if any.
     *
     * @return optional current action execution context
     */
    public static Optional<ActionExecutionContext> currentOptional() {
        Scope scope = CURRENT.get();
        return Optional.ofNullable(scope == null ? null : scope.installed);
    }

    /**
     * Binds a context until the returned scope is closed.
     *
     * <p>This method is primarily used by the engine and by integrations that
     * deliberately propagate action context across a custom synchronous
     * boundary. Scopes are thread-confined, are not inherited by application-created
     * asynchronous tasks, and must be closed in LIFO order.
     *
     * @param context context to bind
     * @return scope restoring the previous context
     */
    public static Scope open(ActionExecutionContext context) {
        return new Scope(Objects.requireNonNull(context, "context"));
    }

    /**
     * Temporarily clears any inherited action context.
     *
     * @return scope restoring the previous context
     */
    public static Scope suspend() {
        return new Scope(null);
    }

    private static void appendIdentityPart(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    /**
     * Returns the process invocation that owns this action attempt.
     *
     * @return process invocation identifier
     */
    public String getProcessInvocationId() {
        return processInvocationId;
    }

    /**
     * Returns the namespace of the executed process.
     *
     * @return process namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the code of the executed process.
     *
     * @return process code
     */
    public String getProcessCode() {
        return processCode;
    }

    /**
     * Returns the source format of the executed process.
     *
     * @return process model type
     */
    public ProcessModelType getModelType() {
        return modelType;
    }

    /**
     * Returns the digest of the exact process source being executed.
     *
     * @return process source digest
     */
    public String getSourceDigest() {
        return sourceDigest;
    }

    /**
     * Returns the node that owns this action.
     *
     * @return process node identifier
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Returns this node's one-based occurrence within the process invocation.
     *
     * @return one-based node invocation ordinal
     */
    public long getInvocationOrdinal() {
        return invocationOrdinal;
    }

    /**
     * Returns the stable opaque key for this logical action invocation.
     *
     * @return artifact-scoped logical invocation key
     */
    public String getInvocationKey() {
        return invocationKey;
    }

    /**
     * Returns the one-based attempt number for this logical action invocation.
     *
     * @return one for the first call, increasing by one for each retry
     */
    public int getAttemptNumber() {
        return attemptNumber;
    }

    private String createInvocationKey() {
        StringBuilder material = new StringBuilder();
        appendIdentityPart(material, "compileflow-action-invocation-v1");
        appendIdentityPart(material, modelType.name());
        appendIdentityPart(material, namespace);
        appendIdentityPart(material, processCode);
        appendIdentityPart(material, sourceDigest);
        appendIdentityPart(material, processInvocationId);
        appendIdentityPart(material, nodeId);
        appendIdentityPart(material, Long.toString(invocationOrdinal));
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256").digest(material.toString().getBytes(StandardCharsets.UTF_8));
            return "cfai_" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /**
     * Thread-confined context scope.
     */
    public static final class Scope implements AutoCloseable {
        private final Thread owner;
        private final ActionExecutionContext installed;
        private final Scope previous;
        private boolean closed;

        private Scope(ActionExecutionContext installed) {
            this.owner = Thread.currentThread();
            this.installed = installed;
            this.previous = CURRENT.get();
            CURRENT.set(this);
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Action execution context scope must be closed by its owner thread");
            }
            if (closed) {
                return;
            }
            if (CURRENT.get() != this) {
                throw new IllegalStateException("Action execution context scopes must be closed in LIFO order");
            }
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
            closed = true;
        }
    }
}
