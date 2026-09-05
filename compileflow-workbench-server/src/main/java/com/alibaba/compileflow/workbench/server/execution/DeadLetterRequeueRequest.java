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

import com.alibaba.compileflow.workbench.server.api.validation.RequestValidationException;
import org.apache.commons.lang3.StringUtils;

/**
 * Bulk dead-letter requeue request.
 *
 * @param processCode optional process code filter
 * @param limit    optional positive batch limit
 * @author yusu
 */
public record DeadLetterRequeueRequest(String processCode, Integer limit) {
    /**
     * Default number of dead-letter executions selected by one request.
     */
    public static final int DEFAULT_LIMIT = 100;
    /**
     * Largest number of dead-letter executions selected by one request.
     */
    public static final int MAX_LIMIT = 500;

    public DeadLetterRequeueRequest {
        processCode = StringUtils.trimToNull(processCode);
        if (limit != null && (limit <= 0 || limit > MAX_LIMIT)) {
            throw new RequestValidationException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    /**
     * Creates an empty filter.
     *
     * @return request using service defaults
     */
    public static DeadLetterRequeueRequest empty() {
        return new DeadLetterRequeueRequest(null, null);
    }

    /**
     * Returns the service batch limit.
     *
     * @return explicit limit or the documented default
     */
    public int effectiveLimit() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }
}
