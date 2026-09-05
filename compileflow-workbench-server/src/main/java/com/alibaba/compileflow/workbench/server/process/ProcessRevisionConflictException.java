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
package com.alibaba.compileflow.workbench.server.process;

/**
 * Indicates that a process draft update lost an optimistic-lock race.
 *
 * @author yusu
 */
final class ProcessRevisionConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    ProcessRevisionConflictException(String code, long expectedRevision, long currentRevision) {
        super(
                "Process revision mismatch: code=" + code + ", expected=" + expectedRevision + ", current=" + currentRevision);
    }
}
