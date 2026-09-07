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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Computes the stable integrity identity of one exact process definition.
 *
 * <p>The digest covers the model type, process code, and exact UTF-8 definition bytes. Publication
 * bindings and release metadata belong to their owning deployment artifact and are deliberately
 * excluded. The domain separator and length-prefixed field order are part of the Supported API
 * contract for the 2.x line.</p>
 *
 * @author yusu
 */
public final class ProcessDefinitionDigest {
    private static final byte[] DOMAIN = "compileflow-process-definition-v1".getBytes(StandardCharsets.UTF_8);

    private ProcessDefinitionDigest() {
    }

    /**
     * Computes a lowercase SHA-256 digest for one inline definition.
     *
     * @param definition exact inline definition
     * @return lowercase SHA-256 digest
     */
    public static String compute(ProcessDefinition.Inline definition) {
        ProcessDefinition.Inline source = Objects.requireNonNull(definition, "definition");
        return compute(source.modelType(), source.code(), source.content().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the same digest directly from exact UTF-8 definition bytes.
     *
     * @param modelType       process definition format
     * @param processCode     process code
     * @param definitionBytes exact UTF-8 definition bytes
     * @return lowercase SHA-256 digest
     */
    public static String compute(ProcessModelType modelType, String processCode, byte[] definitionBytes) {
        MessageDigest digest = sha256();
        update(digest, DOMAIN);
        update(digest, Objects.requireNonNull(modelType, "modelType").name());
        update(digest, ProcessIdentifiers.requireCode(processCode));
        update(digest, Objects.requireNonNull(definitionBytes, "definitionBytes"));
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
