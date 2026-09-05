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
package com.alibaba.compileflow.workbench.server.execution;

/**
 * Indicates that a persisted async payload cannot be decoded or validated.
 *
 * @author yusu
 */
final class AsyncInvocationPayloadException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    AsyncInvocationPayloadException(String fieldName, Throwable cause) {
        super("Invalid async invocation " + fieldName + " JSON payload", cause);
    }
}
