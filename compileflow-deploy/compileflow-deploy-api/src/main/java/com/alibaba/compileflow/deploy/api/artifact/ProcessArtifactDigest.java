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
package com.alibaba.compileflow.deploy.api.artifact;

import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessDefinitionDigest;
import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Computes the integrity identity of one immutable published process artifact.
 *
 * <p>The digest covers the exact definition identity and every source-derived direct call-site
 * binding. Publication coordinates and release metadata are deliberately excluded.</p>
 *
 * @author yusu
 */
public final class ProcessArtifactDigest {
    private static final byte[] DOMAIN = "compileflow-process-artifact-v1".getBytes(StandardCharsets.UTF_8);

    private ProcessArtifactDigest() {
    }

    /**
     * Computes the digest of one published artifact and its direct exact call edges.
     *
     * @param modelType    process definition format
     * @param definition   exact inline definition
     * @param callBindings exact target Version selected for every direct call site
     * @return lowercase SHA-256 digest
     */
    public static String compute(ProcessModelType modelType, ProcessDefinition.Inline definition,
            Map<String, ProcessRef.Version> callBindings) {
        ProcessDefinition.Inline source = Objects.requireNonNull(definition, "definition");
        MessageDigest digest = sha256();
        update(digest, DOMAIN);
        update(digest, ProcessDefinitionDigest.compute(modelType, source));
        Map<String, ProcessRef.Version> bindings =
                new LinkedHashMap<>(Objects.requireNonNull(callBindings, "callBindings"));
        bindings
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                update(digest, ProcessIdentifiers.requireNodeId(entry.getKey()));
                ProcessRef.Version target = Objects.requireNonNull(entry.getValue(), "callBindings contains null");
                update(digest, target.namespace());
                update(digest, target.code());
                update(digest, target.version());
            });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static void update(MessageDigest digest, String value) {
        update(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void update(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }
}
