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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProcessTextTest {
    @Test
    void validatesAndStripsUnicodeText() {
        assertThat(ProcessText.requireUnicode("\uD83D\uDE00", "text")).isEqualTo("\uD83D\uDE00");
        assertThat(ProcessText.strip("\u00a0 value \u3000")).isEqualTo("value");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessText.requireNonBlank("\u00a0\u3000", "text"))
            .withMessage("text must not be blank");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessText.requireUnicode("\ud800", "text"))
            .withMessage("text must be valid Unicode");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessText.requireUnicode("a\u0000b", "text"))
            .withMessage("text must not contain null");
    }

    @Test
    void encodesAndDecodesUtf8Strictly() {
        String unicode = "\u6d41\u7a0b";
        assertThat(ProcessText.decodeUtf8(ProcessText.encodeUtf8(unicode, "text"), "text")).isEqualTo(unicode);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessText.encodeUtf8("\ud800", "text"))
            .withMessage("text must be valid Unicode");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessText.decodeUtf8(new byte[] {(byte) 0xc3, 0x28}, "text"))
            .withMessage("text must be valid UTF-8");
        assertThat(ProcessText.decodeUtf8("\u0000".getBytes(StandardCharsets.UTF_8), "text")).isEqualTo("\u0000");
    }

    @Test
    void truncatesAtCodePointBoundaries() {
        String value = "a\uD83D\uDE00b";

        assertThat(ProcessText.truncateCodePoints(value, 2)).isEqualTo("a\uD83D\uDE00");
        assertThat(ProcessText.truncateCodePoints(value, 0)).isEmpty();
        assertThat(ProcessText.truncateCodePoints(value, 3)).isSameAs(value);
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessText.truncateCodePoints(value, -1))
            .withMessage("maximumCodePoints must not be negative");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> ProcessText.truncateCodePoints("\ud800", 1))
            .withMessage("value must be valid Unicode");
    }
}
