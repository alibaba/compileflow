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
package com.alibaba.compileflow.workbench.server.execution;

import static com.alibaba.compileflow.workbench.server.api.problem.ApiProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AsyncInvocationControllerTest {
    private static ExecutionRoutingRequest aliasRouting(String alias) {
        return new ExecutionRoutingRequest(null, alias, null, Map.of());
    }

    private static AsyncInvocationResponse invocation(String invocationId, String status) {
        return new AsyncInvocationResponse(invocationId, "payment.approve", AsyncInvocationStatus.fromValue(status), 0,
                0L, 0, 1, 0L, null, "2026-07-26T00:00:00Z", "2026-07-26T00:00:00Z", null, null, null, null, null, null,
                null, null, null, null);
    }

    private static AsyncInvocationHealthResponse health(String status, long deadLetterCount) {
        return new AsyncInvocationHealthResponse(status, 0L, 0L, 0L, 0L, 0L, 0L, deadLetterCount, 0L, 0, 0,
                "worker-test", 30000L, 50, "2026-07-26T00:00:00Z");
    }

    @Test
    void submitMapsMissingRoutingValidationTo400() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        when(service.submit(eq("payment.approve"), isNull()))
            .thenThrow(
                    new InvalidAsyncInvocationRequestException(
                            "routing with exactly one of version or alias is required"));
        AsyncInvocationController controller = new AsyncInvocationController(service);

        assertProblem(() -> controller.submitAsyncInvocation("payment.approve", null), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "routing with exactly one of version or alias is required");
        verify(service).submit(eq("payment.approve"), isNull());
    }

    @Test
    void oversizedPayloadIsRejectedAsClientInputBeforeAnyPersistence() {
        AsyncInvocationRepository repository = mock(AsyncInvocationRepository.class);
        AsyncInvocationService service = new AsyncInvocationService(repository, mock(AsyncInvocationStore.class),
                mock(AsyncInvocationWorker.class), mock(PublishedProcessExecutionService.class));
        AsyncInvocationController controller = new AsyncInvocationController(service);
        AsyncInvocationSubmitRequest body = new AsyncInvocationSubmitRequest("inv-oversized",
                Map.of("value", "x".repeat(4 * 1024 * 1024 + 1)), aliasRouting("production"), 1, 0L);

        assertProblem(() -> controller.submitAsyncInvocation("payment.approve", body), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "Async invocation payload exceeds the character limit");
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void submitMapsAmbiguousRoutingValidationTo400() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        when(service.submit(eq("payment.approve"), any(AsyncInvocationSubmitRequest.class)))
            .thenThrow(
                    new InvalidAsyncInvocationRequestException("routing must contain exactly one of version or alias"));
        AsyncInvocationController controller = new AsyncInvocationController(service);
        AsyncInvocationSubmitRequest body = new AsyncInvocationSubmitRequest(null, null,
                new ExecutionRoutingRequest("v2", "production", null, Map.of()), null, null);

        assertProblem(() -> controller.submitAsyncInvocation("payment.approve", body), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "routing must contain exactly one of version or alias");
        verify(service).submit(eq("payment.approve"), any(AsyncInvocationSubmitRequest.class));
    }

    @Test
    void submitReturnsAcceptedInvocation() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationResponse invocation = invocation("inv-client-1", AsyncInvocationService.STATUS_QUEUED);
        when(service.submit(eq("payment.approve"), any(AsyncInvocationSubmitRequest.class))).thenReturn(invocation);
        AsyncInvocationController controller = new AsyncInvocationController(service);
        AsyncInvocationSubmitRequest body =
                new AsyncInvocationSubmitRequest("inv-client-1", Map.of(), aliasRouting("production"), null, null);

        ResponseEntity<AsyncInvocationResponse> response = controller.submitAsyncInvocation("payment.approve", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().invocationId()).isEqualTo("inv-client-1");
        verify(service).submit(eq("payment.approve"), any(AsyncInvocationSubmitRequest.class));
    }

    @Test
    void submitAcceptsAliasAdmissionInputs() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationResponse invocation = invocation("inv-routed", AsyncInvocationService.STATUS_QUEUED);
        when(service.submit(eq("payment.approve"), any(AsyncInvocationSubmitRequest.class))).thenReturn(invocation);
        AsyncInvocationController controller = new AsyncInvocationController(service);
        AsyncInvocationSubmitRequest body = new AsyncInvocationSubmitRequest(null, null,
                new ExecutionRoutingRequest(null, "production", "opaque-user-key", Map.of("region", "eu")), null, null);

        ResponseEntity<AsyncInvocationResponse> response = controller.submitAsyncInvocation("payment.approve", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(service).submit(eq("payment.approve"), any(AsyncInvocationSubmitRequest.class));
    }

    @Test
    void submitMapsInvalidRetryOptionsTo400() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        when(service.submit(eq("payment.approve"), any(AsyncInvocationSubmitRequest.class)))
            .thenThrow(new InvalidAsyncInvocationRequestException("maxAttempts must be between 1 and 100"));
        AsyncInvocationController controller = new AsyncInvocationController(service);
        AsyncInvocationSubmitRequest body =
                new AsyncInvocationSubmitRequest(null, null, aliasRouting("production"), 0, null);

        assertProblem(() -> controller.submitAsyncInvocation("payment.approve", body), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "maxAttempts must be between 1 and 100");
        verify(service).submit(eq("payment.approve"), any(AsyncInvocationSubmitRequest.class));
    }

    @Test
    void requeueMapsStateConflictTo409() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        when(service.requeue("inv-1"))
            .thenThrow(new AsyncInvocationService.AsyncInvocationConflictException("not dead-letter"));
        AsyncInvocationController controller = new AsyncInvocationController(service);

        assertProblem(() -> controller.requeueAsyncInvocation("inv-1"), HttpStatus.CONFLICT, "CONFLICT",
                "not dead-letter");
    }

    @Test
    void requeueRejectsUnsafeInvocationIdBeforeServiceCall() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationController controller = new AsyncInvocationController(service);

        var failure = assertProblem(() -> controller.requeueAsyncInvocation("inv-1\nforged"), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "invocationId must not contain control characters or Unicode format characters");
        assertThat(failure.getBody().getDetail()).contains("control characters");
        verify(service, never()).requeue(any(String.class));
    }

    @Test
    void submitDoesNotExposeUnexpectedServiceFailure() {
        String sensitiveMessage = "database-password=secret";
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        when(service.submit(eq("payment.approve"), any(AsyncInvocationSubmitRequest.class)))
            .thenThrow(new IllegalArgumentException(sensitiveMessage));
        AsyncInvocationController controller = new AsyncInvocationController(service);
        Logger logger = (Logger) LoggerFactory.getLogger(AsyncInvocationController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertProblem(() -> controller.submitAsyncInvocation("payment.approve",
                            new AsyncInvocationSubmitRequest(null, null, aliasRouting("production"), null, null)),
                    HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal async invocation error");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).doesNotContain(sensitiveMessage);
        assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
            .contains(IllegalArgumentException.class.getName())
            .doesNotContain(sensitiveMessage);
    }

    @Test
    void submitMapsInvocationIdRequestConflictTo409() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        when(service.submit(eq("payment.approve"), any(AsyncInvocationSubmitRequest.class)))
            .thenThrow(
                    new AsyncInvocationService.AsyncInvocationConflictException(
                            "invocationId is already bound to a different async request: inv-1"));
        AsyncInvocationController controller = new AsyncInvocationController(service);

        assertProblem(() -> controller.submitAsyncInvocation("payment.approve",
                        new AsyncInvocationSubmitRequest("inv-1", null, aliasRouting("production"), null, null)),
                HttpStatus.CONFLICT, "CONFLICT", "invocationId is already bound to a different async request: inv-1");
    }

    @Test
    void requeueDoesNotExposeUnexpectedServiceFailure() {
        String sensitiveMessage = "database-password=secret";
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        when(service.requeue("inv-1")).thenThrow(new IllegalStateException(sensitiveMessage));
        AsyncInvocationController controller = new AsyncInvocationController(service);

        var failure = assertProblem(() -> controller.requeueAsyncInvocation("inv-1"), HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "Internal async invocation error");
        assertThat(failure.getBody().toString()).doesNotContain(sensitiveMessage);
    }

    @Test
    void getReturnsInvocationWhenPresent() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationResponse invocation = invocation("inv-1", AsyncInvocationService.STATUS_QUEUED);
        when(service.get("inv-1")).thenReturn(Optional.of(invocation));
        AsyncInvocationController controller = new AsyncInvocationController(service);

        ResponseEntity<AsyncInvocationResponse> response = controller.getAsyncInvocation("inv-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().invocationId()).isEqualTo("inv-1");
    }

    @Test
    void getRejectsUnsafeInvocationIdBeforeServiceCall() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationController controller = new AsyncInvocationController(service);

        var failure = assertProblem(() -> controller.getAsyncInvocation("inv-1\nforged"), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "invocationId must not contain control characters or Unicode format characters");
        assertThat(failure.getBody().getDetail()).contains("control characters");
        verify(service, never()).get(any(String.class));
    }

    @Test
    void listAttemptsReturnsBoundedCursorPage() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationAttemptListResponse page = new AsyncInvocationAttemptListResponse(List.of(
                        new AsyncInvocationAttemptResponse("att-1", "inv-1", 1L, 0, 1, "worker-1",
                                AsyncInvocationAttemptOutcome.LEASE_EXPIRED,
                                AsyncInvocationAttemptDisposition.RETRY_SCHEDULED, "2026-07-26T00:00:00Z",
                                "2026-07-26T00:00:01Z", "2026-07-26T00:00:02Z", null, "LEASE_EXPIRED", "lease expired",
                                1000L)), true, 1L);
        when(service.listAttempts("inv-1", 0L, 1)).thenReturn(Optional.of(page));
        AsyncInvocationController controller = new AsyncInvocationController(service);

        ResponseEntity<AsyncInvocationAttemptListResponse> response =
                controller.listAsyncInvocationAttempts("inv-1", 0L, 1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().nextAfterSequence()).isEqualTo(1L);
    }

    @Test
    void listAttemptsRejectsInvalidCursorBeforeServiceCall() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationController controller = new AsyncInvocationController(service);

        assertProblem(() -> controller.listAsyncInvocationAttempts("inv-1", -1L, 20), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "afterSequence must be greater than or equal to 0");
        verify(service, never()).listAttempts(any(String.class), anyLong(), anyInt());
    }

    @Test
    void listAttemptsReturns404ForUnknownInvocation() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        when(service.listAttempts("inv-missing", 0L, 20)).thenReturn(Optional.empty());
        AsyncInvocationController controller = new AsyncInvocationController(service);

        assertProblem(() -> controller.listAsyncInvocationAttempts("inv-missing", 0L, 20), HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND", "Async invocation 'inv-missing' was not found");
    }

    @Test
    void listReturnsPagedInvocations() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationListResponse page = new AsyncInvocationListResponse(List.of(
                        invocation("inv-1", AsyncInvocationService.STATUS_DEAD_LETTER)), 1L, 1, 10);
        when(service.list(AsyncInvocationService.STATUS_DEAD_LETTER, "payment.fail", 1, 10)).thenReturn(page);
        AsyncInvocationController controller = new AsyncInvocationController(service);

        ResponseEntity<AsyncInvocationListResponse> response =
                controller.listAsyncInvocations(AsyncInvocationService.STATUS_DEAD_LETTER, "payment.fail", 1, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().total()).isEqualTo(1L);
        verify(service).list(AsyncInvocationService.STATUS_DEAD_LETTER, "payment.fail", 1, 10);
    }

    @Test
    void listRejectsUnsupportedStatusBeforeServiceCall() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationController controller = new AsyncInvocationController(service);

        assertProblem(() -> controller.listAsyncInvocations("failed", null, 1, 20), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "status must be one of: queued, running, succeeded, dead_letter");
        verify(service, never()).list(any(String.class), any(String.class), anyInt(), anyInt());
    }

    @Test
    void listRejectsZeroPageBeforeServiceCall() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationController controller = new AsyncInvocationController(service);

        assertProblem(() -> controller.listAsyncInvocations(null, null, 0, 20), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "page must be greater than or equal to 1");
        verify(service, never()).list(any(String.class), any(String.class), anyInt(), anyInt());
    }

    @Test
    void listRejectsOversizedPageBeforeServiceCall() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationController controller = new AsyncInvocationController(service);

        assertProblem(() -> controller.listAsyncInvocations(null, null, 1, 101), HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "pageSize must be less than or equal to 100");
        verify(service, never()).list(any(String.class), any(String.class), anyInt(), anyInt());
    }

    @Test
    void healthReturnsQueueSnapshot() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationHealthResponse health = health("degraded", 2L);
        when(service.health()).thenReturn(health);
        AsyncInvocationController controller = new AsyncInvocationController(service);

        ResponseEntity<AsyncInvocationHealthResponse> response = controller.getAsyncInvocationHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("degraded");
        assertThat(response.getBody().deadLetterCount()).isEqualTo(2L);
    }

    @Test
    void requeueDeadLettersReturnsBatchResult() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        AsyncInvocationDeadLetterRequeueResponse result = new AsyncInvocationDeadLetterRequeueResponse(2,
                "2026-07-26T00:00:00Z", "payment.fail", 50, List.of("inv-1", "inv-2"), health("healthy", 0L));
        when(service.requeueDeadLetters("payment.fail", 50)).thenReturn(result);
        AsyncInvocationController controller = new AsyncInvocationController(service);
        DeadLetterRequeueRequest body = new DeadLetterRequeueRequest("payment.fail", 50);

        ResponseEntity<AsyncInvocationDeadLetterRequeueResponse> response =
                controller.requeueAsyncInvocationDeadLetters(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().requeued()).isEqualTo(2);
        verify(service).requeueDeadLetters("payment.fail", 50);
    }

    @Test
    void requeueDeadLettersUsesDocumentedDefaultLimit() {
        AsyncInvocationService service = mock(AsyncInvocationService.class);
        when(service.requeueDeadLetters(null, 100))
            .thenReturn(
                    new AsyncInvocationDeadLetterRequeueResponse(0, "2026-07-26T00:00:00Z", null, 100, List.of(),
                            health("healthy", 0L)));
        AsyncInvocationController controller = new AsyncInvocationController(service);

        ResponseEntity<AsyncInvocationDeadLetterRequeueResponse> response =
                controller.requeueAsyncInvocationDeadLetters(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).requeueDeadLetters(null, 100);
    }
}
