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

import com.alibaba.compileflow.engine.spi.execution.RetryPolicy;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * Built-in retry policies for process action execution.
 *
 * @author yusu
 */
public final class RetryPolicies {
    public static final RetryPolicy ALWAYS = t -> true;
    public static final RetryPolicy NEVER = t -> false;
    public static final RetryPolicy TRANSIENT =
            t -> {
        Set<Throwable> chain = throwableChain(t);
        for (Throwable failure : chain) {
            if (failure instanceof InterruptedException || failure instanceof CancellationException) {
                return false;
            }
        }
        for (Throwable failure : chain) {
            if (failure instanceof ConnectException || failure instanceof SocketTimeoutException
                    || failure instanceof SQLTransientException || failure instanceof SQLRecoverableException) {
                return true;
            }
        }
        return false;
    };

    private RetryPolicies() {
    }

    private static Set<Throwable> throwableChain(Throwable failure) {
        Set<Throwable> chain = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && chain.add(current)) {
            current = current.getCause();
        }
        return chain;
    }
}
