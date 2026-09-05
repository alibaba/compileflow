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

import com.alibaba.compileflow.workbench.server.api.problem.ApiProblems;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties.Authentication;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties.AuthenticationMode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Authenticates Workbench HTTP requests with a configured API key.
 *
 * @author yusu
 */
@Component
@Order(10)
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String SHA_256 = "SHA-256";
    private final boolean authenticationEnabled;
    private final byte[] configuredApiKeyDigest;
    private final String healthEndpointPath;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApiKeyAuthFilter(CompileFlowWorkbenchServerProperties properties, WebEndpointProperties webEndpointProperties,
            ObjectMapper objectMapper) {
        Authentication authentication = properties.getAuthentication();
        this.authenticationEnabled = authentication.getMode() == AuthenticationMode.API_KEY;
        this.configuredApiKeyDigest = authenticationEnabled ? digest(authentication.getApiKey()) : new byte[0];
        this.healthEndpointPath = endpointPath(webEndpointProperties, "health");
        this.objectMapper = objectMapper;
    }

    static boolean matchesApiKey(String providedKey, byte[] configuredDigest) {
        if (configuredDigest == null || configuredDigest.length == 0) {
            return false;
        }
        return MessageDigest.isEqual(digest(providedKey == null ? "" : providedKey), configuredDigest);
    }

    private static String applicationPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isEmpty()) {
            return requestUri;
        }
        return requestUri.substring(contextPath.length());
    }

    private static String endpointPath(WebEndpointProperties properties, String endpointId) {
        String basePath = stripSlashes(properties.getBasePath());
        String mappedPath = stripSlashes(properties.getPathMapping().getOrDefault(endpointId, endpointId));
        return (basePath.isEmpty() ? "" : "/" + basePath) + "/" + mappedPath;
    }

    private static String stripSlashes(String value) {
        if (value == null || value.isBlank() || "/".equals(value)) {
            return "";
        }
        int start = value.startsWith("/") ? 1 : 0;
        int end = value.endsWith("/") ? value.length() - 1 : value.length();
        return value.substring(start, end);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance(SHA_256).digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 digest algorithm is not available", failure);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !authenticationEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = applicationPath(request);
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (matchesApiKey(providedKey, configuredApiKeyDigest)) {
            filterChain.doFilter(request, response);
            return;
        }

        ProblemDetail problem = ApiProblems.create(HttpStatus.UNAUTHORIZED, ApiProblems.UNAUTHENTICATED,
                "Authentication required", "Invalid or missing API key");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(problem.getStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiProblems.body(problem));
    }

    private boolean isPublicPath(String path) {
        return healthEndpointPath.equals(path) || (healthEndpointPath + "/liveness").equals(path)
                || (healthEndpointPath + "/readiness").equals(path);
    }
}
