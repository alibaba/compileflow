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
package com.alibaba.compileflow.durable.runtime.process;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stable persisted identity mapping for an exact Durable Process definition.
 *
 * <p>The mapping takes the first 128 bits of the lowercase SHA-256 definition digest, then sets the
 * UUID version to 8 and the RFC 4122 variant. This exact bit mapping is a persisted compatibility
 * contract: changing it would prevent recovery from finding previously registered Processes.</p>
 */
final class DurableProcessIdentity {
    private DurableProcessIdentity() {
    }

    static UUID fromDefinitionDigest(String definitionDigest) {
        byte[] bytes = HexFormat.of().parseHex(ProcessIdentifiers.requireSha256(definitionDigest, "definitionDigest"));
        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x80);
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
        ByteBuffer value = ByteBuffer.wrap(bytes);
        return new UUID(value.getLong(), value.getLong());
    }
}
