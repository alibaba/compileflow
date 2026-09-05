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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties.Authentication;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties.AuthenticationMode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AuthenticationModeGuardTest {
    private static AuthenticationModeGuard guard(AuthenticationMode mode, MockEnvironment environment) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getMode()).thenReturn(mode);
        CompileFlowWorkbenchServerProperties properties = mock(CompileFlowWorkbenchServerProperties.class);
        when(properties.getAuthentication()).thenReturn(authentication);
        return new AuthenticationModeGuard(properties, environment);
    }

    @Test
    void shouldAllowApiKeyModeWithoutAProfile() {
        assertThatCode(() -> guard(AuthenticationMode.API_KEY, new MockEnvironment()).validateAuthenticationMode())
            .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowDisabledModeOnlyForExplicitLocalProfiles() {
        MockEnvironment development = new MockEnvironment();
        development.setActiveProfiles("dev");
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test", "integration");

        assertThatCode(() -> guard(AuthenticationMode.DISABLED, development).validateAuthenticationMode())
            .doesNotThrowAnyException();
        assertThatCode(() -> guard(AuthenticationMode.DISABLED, test).validateAuthenticationMode()).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDisabledModeWithoutALocalProfile() {
        assertThatThrownBy(() -> guard(AuthenticationMode.DISABLED, new MockEnvironment()).validateAuthenticationMode())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("explicit dev or test profile");
    }

    @Test
    void shouldRejectProductionCombinedWithAnyLocalProfile() {
        MockEnvironment productionDevelopment = new MockEnvironment();
        productionDevelopment.setActiveProfiles("prod", "dev");
        MockEnvironment productionTest = new MockEnvironment();
        productionTest.setActiveProfiles("prod", "test");

        assertThatThrownBy(() -> guard(AuthenticationMode.API_KEY, productionDevelopment).validateAuthenticationMode())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("prod profile must not be combined");
        assertThatThrownBy(() -> guard(AuthenticationMode.DISABLED, productionTest).validateAuthenticationMode())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("prod profile must not be combined");
    }
}
