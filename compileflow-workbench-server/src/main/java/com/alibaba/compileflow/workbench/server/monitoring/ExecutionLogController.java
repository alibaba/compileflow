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
package com.alibaba.compileflow.workbench.server.monitoring;

import com.alibaba.compileflow.workbench.server.api.problem.ApiProblemException;
import com.alibaba.compileflow.workbench.server.api.validation.RequestValueParser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for persisted execution logs.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api/execution-logs")
public class ExecutionLogController {
    private static final String DEFAULT_PAGE = "1";
    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final String MAX_PAGE_SIZE = "" + RequestValueParser.MAX_LIST_PAGE_SIZE;
    private final ExecutionLogService logService;
    private final Clock clock;

    @Autowired
    public ExecutionLogController(ExecutionLogService logService) {
        this(logService, Clock.systemUTC());
    }

    ExecutionLogController(ExecutionLogService logService, Clock clock) {
        this.logService = logService;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static Long parseInstantMillis(String key, String iso) {
        if (iso == null || iso.trim().isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (DateTimeParseException | ArithmeticException failure) {
            throw new IllegalArgumentException(key + " must be an ISO-8601 timestamp");
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String protectedValue = isSpreadsheetFormula(value) ? "'" + value : value;
        if (protectedValue.contains(",") || protectedValue.contains("\"") || protectedValue.contains("\n")
                || protectedValue.contains("\r")) {
            return "\"" + protectedValue.replace("\"", "\"\"") + "\"";
        }
        return protectedValue;
    }

    private static boolean isSpreadsheetFormula(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        if (index == value.length()) {
            return false;
        }
        char first = value.charAt(index);
        return first == '=' || first == '+' || first == '-' || first == '@';
    }

    private static String normalizeStatus(String status) {
        String normalized = StringUtils.trimToNull(status);
        if (normalized == null) {
            return null;
        }
        if ("all".equals(normalized) || "success".equals(normalized) || "failed".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("status must be one of: success, failed, all");
    }

    private static String textOrNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /**
     * Lists execution logs with optional filters and one-based pagination.
     *
     * @param processCode       optional process code filter
     * @param status            optional execution status filter
     * @param keyword           optional keyword matched against process and error fields
     * @param startTime          optional inclusive start timestamp
     * @param endTime            optional inclusive end timestamp
     * @param invocationId       optional invocation id filter
     * @param parentInvocationId optional direct parent invocation id filter
     * @param traceId            optional trace id filter
     * @param callDepth          optional zero-based process-call depth filter
     * @param namespace          optional namespace filter
     * @param requestedVersion   optional requested version filter
     * @param effectiveVersion   optional effective version filter
     * @param routingSource      optional routing source filter
     * @param routeAlias         optional route alias filter
     * @param routeRevision      optional route revision filter
     * @param page               one-based page number
     * @param pageSize           maximum number of logs returned on the page
     * @return page containing matching execution logs and total count metadata
     */
    @GetMapping
    @Parameters({@Parameter(name = "page", in = ParameterIn.QUERY, schema = @Schema(implementation = Integer.class, defaultValue = DEFAULT_PAGE, minimum =
            "1")),
            @Parameter(name = "pageSize", in = ParameterIn.QUERY, schema = @Schema(implementation = Integer.class, defaultValue = DEFAULT_PAGE_SIZE, minimum =
            "1", maximum = MAX_PAGE_SIZE))})
    public ResponseEntity<ExecutionLogListResponse> listExecutionLogs(@RequestParam(required = false) String processCode,
            @Parameter(schema = @Schema(allowableValues = {"success", "failed", "all"})) @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @Parameter(schema = @Schema(format = "date-time")) @RequestParam(required = false) String startTime,
            @Parameter(schema = @Schema(format = "date-time")) @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String invocationId,
            @RequestParam(required = false) String parentInvocationId, @RequestParam(required = false) String traceId,
            @RequestParam(required = false) Integer callDepth, @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String requestedVersion,
            @RequestParam(required = false) String effectiveVersion,
            @RequestParam(required = false) String routingSource, @RequestParam(required = false) String routeAlias,
            @RequestParam(required = false) Long routeRevision, @RequestParam(defaultValue = DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int pageSize) {
        ExecutionLogService.LogQuery query;
        try {
            RequestValueParser.requireOneBasedPage(page, pageSize, RequestValueParser.MAX_LIST_PAGE_SIZE);
            query = query(processCode, status, keyword, startTime, endTime, invocationId, parentInvocationId, traceId,
                    callDepth, namespace, requestedVersion, effectiveVersion, routingSource, routeAlias, routeRevision);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }

        Page<ExecutionLogEntity> logs = logService.search(query, page, pageSize);
        return ResponseEntity.ok(toListResponse(logs, page));
    }

    /**
     * Returns a single execution log by id.
     *
     * @param id execution log id
     * @return execution log details, or {@code 404} when the log does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<ExecutionLogResponse> getExecutionLog(@PathVariable String id) {
        ExecutionLogEntity log = logService
            .findById(id)
            .orElseThrow(() -> ApiProblemException.notFound("Execution log '" + id + "' was not found"));
        return ResponseEntity.ok(toResponse(log));
    }

    /**
     * Exports matching execution logs as a CSV attachment.
     *
     * @param body optional request body containing log filters
     * @return CSV attachment, or {@code 400} when the filters are invalid
     */
    @PostMapping(value = "/export", produces = "text/csv")
    @ApiResponse(responseCode = "200", description = "Raw UTF-8 CSV attachment (not base64 encoded)", content = @Content(mediaType = "t"
            + "ext/csv", schema = @Schema(type = "string", format = "binary")))
    public ResponseEntity<byte[]> exportExecutionLogs(@RequestBody(required = false) ExecutionLogExportRequest body) {
        ExecutionLogService.LogQuery query;
        try {
            ExecutionLogExportRequest request = body == null ? ExecutionLogExportRequest.empty() : body;
            query = query(request.processCode(), request.status(), request.keyword(), request.startTime(),
                    request.endTime(), request.invocationId(), request.parentInvocationId(), request.traceId(),
                    request.callDepth(), request.namespace(), request.requestedVersion(), request.effectiveVersion(),
                    request.routingSource(), request.routeAlias(), request.routeRevision());
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }

        List<ExecutionLogEntity> rows = logService.export(query);

        StringBuilder csv = new StringBuilder(
                "id,processCode,invocationId,parentInvocationId,callDepth,traceId,modelType,sourceDigest,"
                + "status,startTime,endTime,duration,namespace,requestedVersion,effectiveVersion,"
                + "routingSource,routeAlias,routeRevision,errorCode,errorMessage\n");
        for (ExecutionLogEntity entity : rows) {
            ExecutionLogResponse row = toResponse(entity);
            csv
                .append(escapeCsv(row.id()))
                .append(',')
                .append(escapeCsv(row.processCode()))
                .append(',')
                .append(escapeCsv(row.invocationId()))
                .append(',')
                .append(escapeCsv(row.parentInvocationId()))
                .append(',')
                .append(row.callDepth())
                .append(',')
                .append(escapeCsv(row.traceId()))
                .append(',')
                .append(escapeCsv(row.modelType() == null ? null : row.modelType().name()))
                .append(',')
                .append(escapeCsv(row.sourceDigest()))
                .append(',')
                .append(escapeCsv(row.status()))
                .append(',')
                .append(escapeCsv(row.startTime()))
                .append(',')
                .append(escapeCsv(row.endTime()))
                .append(',')
                .append(row.duration())
                .append(',')
                .append(escapeCsv(row.namespace()))
                .append(',')
                .append(escapeCsv(row.requestedVersion()))
                .append(',')
                .append(escapeCsv(row.effectiveVersion()))
                .append(',')
                .append(escapeCsv(row.routingSource()))
                .append(',')
                .append(escapeCsv(row.routeAlias()))
                .append(',')
                .append(row.routeRevision() == null ? "" : row.routeRevision())
                .append(',')
                .append(escapeCsv(row.errorCode()))
                .append(',')
                .append(escapeCsv(row.errorMessage()))
                .append('\n');
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"execution-logs.csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(bytes);
    }

    /**
     * Purges one bounded batch of execution logs older than a cutoff timestamp.
     *
     * @param body request body containing an exclusive ISO-8601 cutoff
     * @return purge count, continuation flag, and completion timestamp
     */
    @PostMapping("/purge")
    public ResponseEntity<PurgeExecutionLogsResponse> purgeExecutionLogs(@RequestBody PurgeExecutionLogsRequest body) {
        if (body == null || body.before() == null) {
            throw ApiProblemException.invalidRequest("before is required");
        }
        long cutoffMs;
        try {
            cutoffMs = parseInstantMillis("before", body.before());
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        ExecutionLogService.PurgeResult result = logService.purgeOlderThan(cutoffMs);
        return ResponseEntity.ok(
                new PurgeExecutionLogsResponse(result.deletedCount(), result.hasMore(), clock.instant().toString()));
    }

    private ExecutionLogListResponse toListResponse(Page<ExecutionLogEntity> logs, int page) {
        return new ExecutionLogListResponse(logs.map(this::toResponse).getContent(), logs.getTotalElements(), page,
                logs.getSize());
    }

    private ExecutionLogResponse toResponse(ExecutionLogEntity entity) {
        String requestedVersion = textOrNull(entity.getRequestedVersion());
        String effectiveVersion = textOrNull(entity.getEffectiveVersion());
        String routingSource = textOrNull(entity.getRoutingSource());
        String routeAlias = textOrNull(entity.getRouteAlias());
        Long routeRevision = entity.getRouteRevision();
        return new ExecutionLogResponse(entity.getId(), entity.getProcessCode(), entity.getInvocationId(),
                textOrNull(entity.getParentInvocationId()), entity.getCallDepth(), entity.getTraceId(),
                entity.getModelType(), textOrNull(entity.getSourceDigest()), entity.getStatus(),
                Instant.ofEpochMilli(entity.getLoggedAt() - entity.getDurationMs()).toString(),
                Instant.ofEpochMilli(entity.getLoggedAt()).toString(), entity.getDurationMs(), entity.getNamespace(),
                requestedVersion, effectiveVersion, routingSource, routeAlias, routeRevision,
                textOrNull(entity.getErrorCode()), textOrNull(entity.getErrorMessage()));
    }

    private static ExecutionLogService.LogQuery query(String processCode, String status, String keyword,
            String startTime, String endTime, String invocationId, String parentInvocationId, String traceId,
            Integer callDepth, String namespace, String requestedVersion, String effectiveVersion, String routingSource,
            String routeAlias, Long routeRevision) {
        return new ExecutionLogService.LogQuery(StringUtils.trimToNull(processCode), normalizeStatus(status),
                StringUtils.trimToNull(keyword), parseInstantMillis("startTime", startTime),
                parseInstantMillis("endTime", endTime), StringUtils.trimToNull(invocationId),
                StringUtils.trimToNull(parentInvocationId), StringUtils.trimToNull(traceId), callDepth,
                StringUtils.trimToNull(namespace), StringUtils.trimToNull(requestedVersion),
                StringUtils.trimToNull(effectiveVersion), StringUtils.trimToNull(routingSource),
                StringUtils.trimToNull(routeAlias), routeRevision);
    }
}
