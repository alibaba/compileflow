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
import com.alibaba.compileflow.workbench.server.api.validation.RequestValidationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders Spring MVC failures through the server's single RFC 9457 error contract.
 *
 * @author yusu
 */
@RestControllerAdvice
public final class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        RequestBodySizeFilter.RequestBodyTooLargeException oversized =
                findCause(exception, RequestBodySizeFilter.RequestBodyTooLargeException.class);
        if (oversized != null) {
            ProblemDetail problem = ApiProblems.create(HttpStatus.CONTENT_TOO_LARGE, ApiProblems.PAYLOAD_TOO_LARGE,
                    "Payload too large", oversized.getMessage());
            return handleExceptionInternal(exception, problem, headers, HttpStatus.CONTENT_TOO_LARGE, request);
        }
        RequestValidationException validation = findCause(exception, RequestValidationException.class);
        if (validation != null) {
            ProblemDetail problem = ApiProblems.create(HttpStatus.BAD_REQUEST, ApiProblems.INVALID_REQUEST,
                    "Invalid request", validation.getMessage());
            return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
        }
        ProblemDetail problem = ApiProblems.create(HttpStatus.BAD_REQUEST, ApiProblems.MALFORMED_REQUEST_BODY,
                "Malformed request body", "The request body is invalid or malformed");
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ApiProblems.create(HttpStatus.BAD_REQUEST, ApiProblems.INVALID_REQUEST,
                "Invalid request", "The request body failed validation");
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception instanceof MissingRequestHeaderException missingHeader) {
            ProblemDetail problem = ApiProblems.create(HttpStatus.BAD_REQUEST, ApiProblems.INVALID_REQUEST,
                    "Invalid request", missingHeader.getHeaderName() + " is required");
            return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
        }
        return super.handleServletRequestBindingException(exception, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            ApiProblems.normalize(problem, status);
        }
        return super.createResponseEntity(body, headers, status, request);
    }
}
