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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties.Authentication;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties.AuthenticationMode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class ApiKeyAuthFilterTest {
    private static CompileFlowWorkbenchServerProperties properties(AuthenticationMode mode, String apiKey) {
        CompileFlowWorkbenchServerProperties properties = mock(CompileFlowWorkbenchServerProperties.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getMode()).thenReturn(mode);
        when(authentication.getApiKey()).thenReturn(apiKey);
        when(properties.getAuthentication()).thenReturn(authentication);
        return properties;
    }

    private static ApiKeyAuthFilter filter(AuthenticationMode mode, String apiKey) {
        WebEndpointProperties endpoints = new WebEndpointProperties();
        endpoints.setBasePath("/actuator");
        return filter(mode, apiKey, endpoints);
    }

    private static ApiKeyAuthFilter filter(AuthenticationMode mode, String apiKey, WebEndpointProperties endpoints) {
        return new ApiKeyAuthFilter(properties(mode, apiKey), endpoints, new ObjectMapper());
    }

    @Test
    void passesThroughWhenAuthenticationIsDisabled() throws Exception {
        ApiKeyAuthFilter filter = filter(AuthenticationMode.DISABLED, "");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/processes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(((HttpServletRequest) chain.getRequest()).getRequestURI()).isEqualTo("/api/processes");
    }

    @Test
    void rejectsMissingKeyWhenConfigured() throws Exception {
        ApiKeyAuthFilter filter = filter(AuthenticationMode.API_KEY, "secret-key");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/processes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(response.getContentAsString()).contains("\"instance\":\"/api/processes\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void acceptsMatchingKey() throws Exception {
        ApiKeyAuthFilter filter = filter(AuthenticationMode.API_KEY, "secret-key");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/processes");
        request.addHeader("X-API-Key", "secret-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(((HttpServletRequest) chain.getRequest()).getRequestURI()).isEqualTo("/api/processes");
    }

    @Test
    void rejectsPaddedRequestKeyEvenWhenConfiguredKeyIsTrimmed() throws Exception {
        ApiKeyAuthFilter filter = filter(AuthenticationMode.API_KEY, "secret-key");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/processes");
        request.addHeader("X-API-Key", " secret-key ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsDifferentLengthKeyWithMatchingPrefix() throws Exception {
        ApiKeyAuthFilter filter = filter(AuthenticationMode.API_KEY, "secret-key");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/processes");
        request.addHeader("X-API-Key", "secret-key-extra");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void allowsActuatorHealthWithoutKey() throws Exception {
        ApiKeyAuthFilter filter = filter(AuthenticationMode.API_KEY, "secret-key");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(((HttpServletRequest) chain.getRequest()).getRequestURI()).isEqualTo("/actuator/health");
    }

    @Test
    void allowsConfiguredActuatorPathBelowApplicationContext() throws Exception {
        WebEndpointProperties endpoints = new WebEndpointProperties();
        endpoints.setBasePath("/manage");
        endpoints.getPathMapping().put("health", "healthcheck");
        ApiKeyAuthFilter filter = filter(AuthenticationMode.API_KEY, "secret-key", endpoints);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/workbench/manage/healthcheck/readiness");
        request.setContextPath("/workbench");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectsPathsThatOnlySharePublicActuatorPrefix() throws Exception {
        ApiKeyAuthFilter filter = filter(AuthenticationMode.API_KEY, "secret-key");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/healthz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void requiresAuthenticationForOtherHealthGroupsAndComponents() throws Exception {
        ApiKeyAuthFilter filter = filter(AuthenticationMode.API_KEY, "secret-key");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/database");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
}
