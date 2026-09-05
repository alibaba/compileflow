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
package com.alibaba.compileflow.workbench.server.api.validation;

/**
 * Identifies a semantically invalid request value discovered during JSON construction.
 *
 * <p>The HTTP boundary uses this type to distinguish valid JSON with invalid field values from
 * malformed JSON or incompatible JSON types.
 *
 * @author yusu
 */
public final class RequestValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a request validation failure.
     *
     * @param message safe client-facing validation detail
     */
    public RequestValidationException(String message) {
        super(message);
    }

    /**
     * Wraps validation delegated to a shared domain value object.
     *
     * @param message safe client-facing validation detail
     * @param cause   original validation failure
     */
    public RequestValidationException(String message, IllegalArgumentException cause) {
        super(message, cause);
    }
}
