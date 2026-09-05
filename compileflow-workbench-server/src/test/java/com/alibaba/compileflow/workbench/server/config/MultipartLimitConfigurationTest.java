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

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.unit.DataSize;

@SpringBootTest(properties = "compileflow.workbench.server.http.max-request-size=2MB")
@ActiveProfiles("test")
class MultipartLimitConfigurationTest {
    @Autowired
    private CompileFlowWorkbenchServerProperties serverProperties;
    @Autowired
    private MultipartProperties multipartProperties;

    @Test
    void appliesTheCanonicalRequestLimitToMultipartParsing() {
        DataSize configuredLimit = serverProperties.getHttp().getMaxRequestSize();

        assertThat(configuredLimit).isEqualTo(DataSize.ofMegabytes(2));
        assertThat(multipartProperties.getMaxFileSize()).isEqualTo(configuredLimit);
        assertThat(multipartProperties.getMaxRequestSize()).isEqualTo(configuredLimit);
    }
}
