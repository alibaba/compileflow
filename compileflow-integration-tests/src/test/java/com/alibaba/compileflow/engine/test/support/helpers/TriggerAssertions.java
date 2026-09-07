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
package com.alibaba.compileflow.engine.test.support.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import com.alibaba.compileflow.engine.ProcessResult;
import java.util.Map;

public final class TriggerAssertions {
    private TriggerAssertions() {
    }

    public static void assertSuccess(ProcessResult<?> result, String message) {
        assertThat(result).as("process result should not be null").isNotNull();
        assertThat(result.isSuccess())
            .as(message + (result.getError() == null ? "" : ("; error=" + result.getError())))
            .isTrue();
    }

    public static void assertSuccessAndDataNotNull(ProcessResult<?> result, String message) {
        assertSuccess(result, message);
        assertThat(result.getOutput()).as("result data should not be null").isNotNull();
    }

    public static void assertSuccessAndHasKey(ProcessResult<Map<String, Object>> result, String key, String message) {
        assertSuccessAndDataNotNull(result, message);
        assertThat(result.getOutput()).as("result data should contain key '%s'", key).containsKey(key);
    }
}
