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
package com.alibaba.compileflow.engine.core.runtime.execution.retry;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class RetryPoliciesTest {
    @Test
    void classifiesStableTransientTypesAnywhereInTheCauseChain() {
        assertThat(RetryPolicies.TRANSIENT.shouldRetryOn(new RuntimeException(new ConnectException()))).isTrue();
        assertThat(RetryPolicies.TRANSIENT.shouldRetryOn(new RuntimeException(new SocketTimeoutException()))).isTrue();
        assertThat(RetryPolicies.TRANSIENT.shouldRetryOn(new RuntimeException(new SQLTransientException()))).isTrue();
        assertThat(RetryPolicies.TRANSIENT.shouldRetryOn(new RuntimeException(new SQLRecoverableException()))).isTrue();
    }

    @Test
    void doesNotParseMessagesOrTreatGenericInterruptedIoAsRetryable() {
        assertThat(RetryPolicies.TRANSIENT.shouldRetryOn(new SocketException("connection reset"))).isFalse();
        assertThat(RetryPolicies.TRANSIENT.shouldRetryOn(new InterruptedIOException("timed out"))).isFalse();
    }

    @Test
    void cancellationOrInterruptionWinsOverNestedTransientFailures() {
        InterruptedException interrupted = new InterruptedException("stop");
        interrupted.initCause(new ConnectException());
        CancellationException cancelled = new CancellationException("stop");
        cancelled.initCause(new SQLTransientException());

        assertThat(RetryPolicies.TRANSIENT.shouldRetryOn(interrupted)).isFalse();
        assertThat(RetryPolicies.TRANSIENT.shouldRetryOn(cancelled)).isFalse();
    }
}
