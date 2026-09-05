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
package com.alibaba.compileflow.deploy.api.release;

import com.alibaba.compileflow.engine.ProcessIdentifiers;
import com.alibaba.compileflow.engine.ProcessText;

/**
 * Validation rules for human-authored deployment audit facts.
 *
 * @author yusu
 */
public final class DeploymentAudit {
    public static final int MAX_ACTOR_CHARACTERS = 128;
    public static final int MAX_NOTES_CHARACTERS = 2_048;

    private DeploymentAudit() {
    }

    /**
     * Validates a required deployment actor.
     *
     * @param value actor value
     * @return validated actor
     */
    public static String requireActor(String value) {
        return ProcessIdentifiers.requireExactIdentity(value, "actor", MAX_ACTOR_CHARACTERS);
    }

    /**
     * Normalizes optional human-authored audit notes.
     *
     * @param value notes value
     * @param name field name used in validation failures
     * @return normalized notes, or {@code null} when absent
     */
    public static String optionalNotes(String value, String name) {
        if (value == null) {
            return null;
        }
        ProcessText.requireUnicode(value, name);
        String normalized = ProcessText.strip(value);
        if (normalized.isEmpty()) {
            return null;
        }
        requireMaximumCharacters(normalized, name, MAX_NOTES_CHARACTERS);
        return normalized;
    }

    private static void requireMaximumCharacters(String value, String name, int maximumCharacters) {
        if (value.codePointCount(0, value.length()) > maximumCharacters) {
            throw new IllegalArgumentException(name + " must not exceed " + maximumCharacters + " characters");
        }
    }
}
