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

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Applies the Workbench request-body limit to Servlet multipart parsing.
 *
 * @author yusu
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.servlet.multipart", name = "enabled", havingValue = "true", matchIfMissing = true)
class MultipartLimitConfiguration {
    @Bean
    MultipartConfigElement workbenchMultipartConfigElement(CompileFlowWorkbenchServerProperties serverProperties,
            MultipartProperties multipartProperties) {
        DataSize requestLimit = serverProperties.getHttp().getMaxRequestSize();
        multipartProperties.setMaxFileSize(requestLimit);
        multipartProperties.setMaxRequestSize(requestLimit);
        return multipartProperties.createMultipartConfig();
    }
}
