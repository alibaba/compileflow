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

import com.alibaba.compileflow.workbench.server.api.problem.ApiProblems;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces a byte limit on request bodies before message conversion can allocate unbounded data.
 *
 * @author yusu
 */
@Component
@Order(20)
public final class RequestBodySizeFilter extends OncePerRequestFilter {
    private final long maxRequestBytes;
    private final ObjectMapper objectMapper;

    @Autowired
    public RequestBodySizeFilter(CompileFlowWorkbenchServerProperties properties, ObjectMapper objectMapper) {
        this(properties.getHttp().getMaxRequestSize().toBytes(), objectMapper);
    }

    RequestBodySizeFilter(long maxRequestBytes, ObjectMapper objectMapper) {
        if (maxRequestBytes <= 0) {
            throw new IllegalArgumentException("maxRequestBytes must be positive");
        }
        this.maxRequestBytes = maxRequestBytes;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxRequestBytes) {
            writePayloadTooLarge(request, response, maxRequestBytes);
            return;
        }

        LimitedRequest limitedRequest = new LimitedRequest(request, maxRequestBytes);
        try {
            filterChain.doFilter(limitedRequest, response);
        } catch (RequestBodyTooLargeException exception) {
            if (response.isCommitted()) {
                throw exception;
            }
            writePayloadTooLarge(request, response, exception.getMaxBytes());
        }
    }

    private void writePayloadTooLarge(HttpServletRequest request, HttpServletResponse response, long maxBytes)
            throws IOException {
        ProblemDetail problem = ApiProblems.create(HttpStatus.CONTENT_TOO_LARGE, ApiProblems.PAYLOAD_TOO_LARGE,
                "Payload too large", "Request body exceeds " + maxBytes + " bytes");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiProblems.body(problem));
    }

    static final class RequestBodyTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final long maxBytes;

        RequestBodyTooLargeException(long maxBytes) {
            super("Request body exceeds " + maxBytes + " bytes");
            this.maxBytes = maxBytes;
        }

        long getMaxBytes() {
            return maxBytes;
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream inputStream;
        private BufferedReader reader;

        private LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (reader != null) {
                throw new IllegalStateException("getReader() has already been called");
            }
            if (inputStream == null) {
                inputStream = new LimitedServletInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader != null) {
                return reader;
            }
            if (inputStream != null) {
                throw new IllegalStateException("getInputStream() has already been called");
            }
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            inputStream = new LimitedServletInputStream(super.getInputStream(), maxBytes);
            reader = new BufferedReader(new InputStreamReader(inputStream, charset));
            return reader;
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private static final int SKIP_BUFFER_SIZE = 8192;
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long remaining;

        private LimitedServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
            this.remaining = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value == -1) {
                return -1;
            }
            consume(1);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, Objects.requireNonNull(bytes, "bytes").length);
            if (length == 0) {
                return 0;
            }
            int permitted = remaining >= length ? length : (int) remaining + 1;
            int count = delegate.read(bytes, offset, permitted);
            if (count > 0) {
                consume(count);
            }
            return count;
        }

        @Override
        public long skip(long count) throws IOException {
            if (count <= 0) {
                return 0;
            }
            byte[] buffer = new byte[(int) Math.min(count, SKIP_BUFFER_SIZE)];
            long skipped = 0;
            while (skipped < count) {
                int read = read(buffer, 0, (int) Math.min(buffer.length, count - skipped));
                if (read == -1) {
                    break;
                }
                skipped += read;
            }
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(delegate.available(), remaining);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private void consume(int count) {
            if (count > remaining) {
                throw new RequestBodyTooLargeException(maxBytes);
            }
            remaining -= count;
        }
    }
}
