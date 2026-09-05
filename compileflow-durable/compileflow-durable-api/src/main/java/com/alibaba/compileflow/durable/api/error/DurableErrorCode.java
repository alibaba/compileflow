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
package com.alibaba.compileflow.durable.api.error;

/**
 * Bounded failure categories returned by Durable Process operations.
 *
 * <p>This Developer Preview contract classifies failures observed by an API
 * caller. Persisted Run, Effect, and Outbox diagnostic codes are a separate
 * storage concern and must not be inferred from these enum names.</p>
 *
 * @author yusu
 */
public enum DurableErrorCode {
    INVALID_ARGUMENT,
    RUN_NOT_FOUND,
    RUN_ALREADY_EXISTS,
    VERSION_NOT_FOUND,
    PROCESS_NOT_FOUND,
    UNSUPPORTED_PROCESS,
    PROCESS_IDENTITY_MISMATCH,
    ARTIFACT_DIGEST_MISMATCH,
    INVALID_WAIT_TOKEN,
    WAIT_COMPLETION_MISMATCH,
    EFFECT_NOT_FOUND,
    OUTBOX_EVENT_NOT_FOUND,
    RUN_CONTROL_NOT_ALLOWED,
    CONCURRENT_MODIFICATION,
    STORE_UNAVAILABLE,
    INTERNAL_ERROR
}
