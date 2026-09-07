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
package com.alibaba.compileflow.durable.runtime.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DurableKernelJsonCodecTest {
    private final DurableKernelJsonCodec codec = new DurableKernelJsonCodec();

    @Test
    void rejectsTrailingDocumentsAndGarbage() {
        for (String suffix : java.util.List.of(" {}", " true", " garbage")) {
            assertThatThrownBy(() -> codec.decode(("{}" + suffix).getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void preservesTheNarrowestExactIntegralScalarAtJavaBoundaries() {
        Map<String, Object> decoded = codec.decode(("""
            {"intMin":-2147483648,"intMax":2147483647,
             "belowInt":-2147483649,"aboveInt":2147483648,
             "longMin":-9223372036854775808,"longMax":9223372036854775807,
             "belowLong":-9223372036854775809,"aboveLong":9223372036854775808}
            """)
            .getBytes(StandardCharsets.UTF_8));

        assertThat(decoded.get("intMin")).isEqualTo(Integer.MIN_VALUE).isInstanceOf(Integer.class);
        assertThat(decoded.get("intMax")).isEqualTo(Integer.MAX_VALUE).isInstanceOf(Integer.class);
        assertThat(decoded.get("belowInt")).isEqualTo((long) Integer.MIN_VALUE - 1L).isInstanceOf(Long.class);
        assertThat(decoded.get("aboveInt")).isEqualTo((long) Integer.MAX_VALUE + 1L).isInstanceOf(Long.class);
        assertThat(decoded.get("longMin")).isEqualTo(Long.MIN_VALUE).isInstanceOf(Long.class);
        assertThat(decoded.get("longMax")).isEqualTo(Long.MAX_VALUE).isInstanceOf(Long.class);
        assertThat(decoded.get("belowLong")).isEqualTo(new BigInteger("-9223372036854775809"));
        assertThat(decoded.get("aboveLong")).isEqualTo(new BigInteger("9223372036854775808"));
    }

    @Test
    void rejectsUnicodeWhitespaceAndReservedKeys() {
        assertThatThrownBy(() -> codec.encode(Map.of("\u00a0key", "value")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Kernel JSON key is invalid or reserved");
        assertThatThrownBy(() -> codec.encode(Map.of("@type", "value")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Kernel JSON key is invalid or reserved");
    }
}
