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
package com.alibaba.compileflow.workbench.server.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.databind.ObjectMapper;

class RequestBodySizeFilterTest {
    private static MockHttpServletRequest requestWithBody(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/test");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static HttpServletRequest withoutDeclaredLength(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
    }

    @Test
    void shouldRejectDeclaredOversizedBodyBeforeInvokingTheApplication() throws Exception {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(3, new ObjectMapper());
        MockHttpServletRequest request = requestWithBody("four");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(MediaType
            .parseMediaType(response.getContentType())
            .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .isTrue();
        assertThat(response.getContentAsString()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
        assertThat(response.getContentAsString()).contains("Request body exceeds 3 bytes");
    }

    @Test
    void shouldRejectChunkedOrUnknownLengthBodyWhileItIsRead() throws Exception {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(3, new ObjectMapper());
        HttpServletRequest request = withoutDeclaredLength(requestWithBody("four"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (boundedRequest, ignoredResponse) -> boundedRequest
            .getInputStream()
            .readAllBytes());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"title\":\"Payload too large\"");
    }

    @Test
    void shouldPassBodiesAtTheConfiguredLimit() throws Exception {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(4, new ObjectMapper());
        HttpServletRequest request = withoutDeclaredLength(requestWithBody("four"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (boundedRequest, ignoredResponse) -> {
            assertThat(boundedRequest.getInputStream().readAllBytes()).isEqualTo("four"
                .getBytes(StandardCharsets.UTF_8));
            invoked.set(true);
        });

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldSupportAnUnboundedLongLimitWithoutOverflowingTheReadLength() throws Exception {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(Long.MAX_VALUE, new ObjectMapper());
        HttpServletRequest request = withoutDeclaredLength(requestWithBody("body"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (boundedRequest, ignoredResponse) -> {
            assertThat(boundedRequest.getInputStream().readAllBytes()).isEqualTo("body"
                .getBytes(StandardCharsets.UTF_8));
            invoked.set(true);
        });

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldMapNestedBodyLimitFailureToHttp413() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("Unreadable request body",
                new RequestBodySizeFilter.RequestBodyTooLargeException(3), mock(HttpInputMessage.class));

        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(exception, HttpHeaders.EMPTY,
                HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));
        ProblemDetail problem = (ProblemDetail) response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(problem.getDetail()).isEqualTo("Request body exceeds 3 bytes");
        assertThat(problem.getProperties()).containsEntry("code", "PAYLOAD_TOO_LARGE");
    }

    @Test
    void shouldKeepMalformedJsonAsHttp400WithoutExposingParserDetails() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("parser implementation detail", mock(HttpInputMessage.class));

        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(exception, HttpHeaders.EMPTY,
                HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));
        ProblemDetail problem = (ProblemDetail) response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(problem.getDetail()).isEqualTo("The request body is invalid or malformed");
        assertThat(problem.getProperties()).containsEntry("code", "MALFORMED_REQUEST_BODY");
    }
}
