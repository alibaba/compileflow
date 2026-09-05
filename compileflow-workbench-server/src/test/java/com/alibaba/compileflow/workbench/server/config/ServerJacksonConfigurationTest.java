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
package com.alibaba.compileflow.workbench.server.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.workbench.server.monitoring.PurgeExecutionLogsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class ServerJacksonConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(ServerJacksonConfiguration.class);

    @Test
    void springManagedMapperEnforcesStrictTransportContract() {
        contextRunner.run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);

            assertThatThrownBy(() -> mapper.readValue("{\"before\":\"2026-07-25T00:00:00Z\"," + "\"extra\":true}",
                    PurgeExecutionLogsRequest.class))
                .isInstanceOf(JacksonException.class);
            assertThatThrownBy(() -> mapper.readValue("{\"before\":42}", PurgeExecutionLogsRequest.class))
                .isInstanceOf(JacksonException.class);
        });
    }
}
