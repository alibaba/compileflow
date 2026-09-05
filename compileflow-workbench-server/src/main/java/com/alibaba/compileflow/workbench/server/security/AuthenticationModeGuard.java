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
package com.alibaba.compileflow.workbench.server.security;

import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties.AuthenticationMode;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Prevents built-in HTTP authentication from being disabled outside explicit local profiles.
 *
 * @author yusu
 */
@Component
final class AuthenticationModeGuard {
    private final CompileFlowWorkbenchServerProperties properties;
    private final Environment environment;

    AuthenticationModeGuard(CompileFlowWorkbenchServerProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validateAuthenticationMode() {
        boolean localProfile = environment.acceptsProfiles(Profiles.of("dev", "test"));
        boolean productionProfile = environment.acceptsProfiles(Profiles.of("prod"));
        if (localProfile && productionProfile) {
            throw new IllegalStateException("The prod profile must not be combined with the dev or test profile");
        }
        if (properties.getAuthentication().getMode() == AuthenticationMode.DISABLED && !localProfile) {
            throw new IllegalStateException(
                    "compileflow.workbench.server.authentication.mode=DISABLED is allowed only with an explicit dev or test profile");
        }
    }
}
