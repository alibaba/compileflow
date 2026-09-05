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
package com.alibaba.compileflow.workbench.server.api.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatusCode;

/**
 * Shared assertions for controller methods that intentionally raise an API problem.
 *
 * @author yusu
 */
public final class ApiProblemAssertions {
    private ApiProblemAssertions() {
    }

    public static ApiProblemException assertProblem(ThrowingCallable invocation, HttpStatusCode status, String code,
            String detail) {
        Throwable thrown = catchThrowable(invocation);
        assertThat(thrown).isInstanceOf(ApiProblemException.class);
        ApiProblemException failure = (ApiProblemException) thrown;
        assertThat(failure.getStatusCode()).isEqualTo(status);
        assertThat(failure.getBody().getDetail()).isEqualTo(detail);
        assertThat(failure.getBody().getProperties()).containsEntry("code", code);
        return failure;
    }
}
