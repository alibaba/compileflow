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
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Supported Unicode validation and whitespace handling for public protocol values.
 *
 * <p>The methods distinguish exact identity validation from explicit presentation-text
 * normalization. Callers must choose the operation that matches the owning field's contract.
 *
 * @author yusu
 */
public final class ProcessText {
    private ProcessText() {
    }

    /**
     * Requires well-formed, NUL-free Unicode text and preserves it exactly.
     *
     * @param value text value
     * @param name field name used in failures
     * @return the validated text unchanged
     */
    public static String requireUnicode(String value, String name) {
        String text = Objects.requireNonNull(value, name);
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\u0000') {
                throw new IllegalArgumentException(name + " must not contain null");
            }
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1))) {
                    throw new IllegalArgumentException(name + " must be valid Unicode");
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException(name + " must be valid Unicode");
            }
        }
        return text;
    }

    /**
     * Requires non-blank, well-formed Unicode text and preserves it exactly.
     *
     * @param value text value
     * @param name field name used in failures
     * @return the validated text unchanged
     */
    public static String requireNonBlank(String value, String name) {
        String text = requireUnicode(value, name);
        if (text.codePoints().allMatch(ProcessText::isWhitespace)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    /**
     * Removes all Unicode whitespace from both boundaries.
     *
     * @param value text value
     * @return stripped text
     */
    public static String strip(String value) {
        Objects.requireNonNull(value, "value");
        int start = 0;
        while (start < value.length()) {
            int codePoint = value.codePointAt(start);
            if (!isWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        int end = value.length();
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    /**
     * Returns at most the requested number of Unicode code points from the start of the value.
     *
     * @param value text value
     * @param maximumCodePoints maximum number of code points to retain
     * @return the original value when it is within the bound, otherwise a code-point-safe prefix
     */
    public static String truncateCodePoints(String value, int maximumCodePoints) {
        requireUnicode(value, "value");
        if (maximumCodePoints < 0) {
            throw new IllegalArgumentException("maximumCodePoints must not be negative");
        }
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maximumCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximumCodePoints));
    }

    /**
     * Encodes text as strict UTF-8 without replacing malformed Unicode.
     *
     * @param value text value
     * @param name field name used in failures
     * @return encoded bytes
     */
    public static byte[] encodeUtf8(String value, String name) {
        Objects.requireNonNull(value, name);
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException(name + " must be valid Unicode", failure);
        }
    }

    /**
     * Decodes strict UTF-8 without replacing malformed byte sequences.
     *
     * @param value encoded bytes
     * @param name field name used in failures
     * @return decoded text
     */
    public static String decodeUtf8(byte[] value, String name) {
        Objects.requireNonNull(value, "value");
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value))
                .toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException(name + " must be valid UTF-8", failure);
        }
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
