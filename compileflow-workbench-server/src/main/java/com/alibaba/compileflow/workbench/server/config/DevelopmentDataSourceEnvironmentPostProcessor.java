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

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * Validates the external PostgreSQL credentials required by the development profile.
 *
 * @author yusu
 */
public final class DevelopmentDataSourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String DATASOURCE_PASSWORD_PROPERTY = "spring.datasource.password";
    private static final String DEVELOPMENT_PROFILE = "dev";

    /**
     * Runs after config data and the server environment adapter have populated the environment.
     *
     * @return the post-processor ordering value
     */
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 2;
    }

    /**
     * Rejects a development startup without an explicit database password.
     *
     * @param environment application environment containing the effective configuration
     * @param application application being started
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of(DEVELOPMENT_PROFILE))) {
            return;
        }

        String password = environment.getProperty(DATASOURCE_PASSWORD_PROPERTY);
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "The dev profile requires a non-empty spring.datasource.password (for example SPRING_"
                    + "DATASOURCE_PASSWORD); no embedded database fallback is available.");
        }
    }
}
