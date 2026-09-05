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

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable process failure reported to an engine caller.
 *
 * <p>The code is stable and machine-readable. The message is safe, human-readable context and
 * must not contain process variables, source content, routing keys, credentials, or raw
 * exception objects.
 *
 * @author yusu
 */
public final class ProcessError implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final int MAX_CODE_LENGTH = 128;
    private static final int MAX_MESSAGE_LENGTH = 4_096;
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_.-]*");
    /**
     * Stable machine-readable error code.
     */
    private final String code;
    /**
     * Safe human-readable failure description.
     */
    private final String message;

    /**
     * Creates an immutable process error.
     *
     * @param code    stable machine-readable error code
     * @param message safe human-readable failure description
     */
    public ProcessError(String code, String message) {
        this.code = requireCode(code);
        this.message = requireMessage(message);
    }

    private static String requireCode(String value) {
        String candidate = ProcessIdentifiers.requireExactIdentity(value, "code", MAX_CODE_LENGTH);
        if (!CODE_PATTERN.matcher(candidate).matches()) {
            throw new IllegalArgumentException("code must start with A-Z and contain only A-Z, 0-9, '.', '_', or '-'");
        }
        return candidate;
    }

    private static String requireMessage(String value) {
        String candidate = ProcessText.requireNonBlank(value, "message");
        if (candidate.codePointCount(0, candidate.length()) > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must not exceed " + MAX_MESSAGE_LENGTH + " characters");
        }
        return candidate;
    }

    /**
     * Returns the stable machine-readable error code.
     *
     * @return validated error code
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the safe human-readable failure description.
     *
     * @return non-blank failure description
     */
    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProcessError that)) {
            return false;
        }
        return code.equals(that.code) && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message);
    }

    @Override
    public String toString() {
        return "ProcessError{code='" + code + "', message=<redacted>}";
    }
}
