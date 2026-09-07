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
package com.alibaba.compileflow.workbench.server.process;

import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessExecutionOptions;
import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.deploy.api.ProcessDeploymentService;
import com.alibaba.compileflow.deploy.api.command.PublishProcessVersionCommand;
import com.alibaba.compileflow.deploy.api.error.DeploymentErrorCode;
import com.alibaba.compileflow.deploy.api.error.DeploymentException;
import com.alibaba.compileflow.deploy.api.release.ReleaseMetadataKeys;
import com.alibaba.compileflow.deploy.api.rollout.RolloutConstraints;
import com.alibaba.compileflow.deploy.api.version.PublishedProcessVersion;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionCursor;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionPage;
import com.alibaba.compileflow.deploy.api.version.PublishedVersionQuery;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import com.alibaba.compileflow.workbench.server.execution.ExecutionRoutingRequest;
import com.alibaba.compileflow.workbench.server.execution.ProcessExecutionRequest;
import com.alibaba.compileflow.workbench.server.execution.ProcessExecutionResponse;
import com.alibaba.compileflow.workbench.server.execution.PublishedProcessExecutionService;
import com.alibaba.compileflow.workbench.server.api.problem.ApiProblemException;
import com.alibaba.compileflow.workbench.server.api.problem.RedactedFailure;
import com.alibaba.compileflow.workbench.server.api.validation.RequestValueParser;
import com.alibaba.compileflow.workbench.server.security.ServerIdentity;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API for draft and published process definitions.
 *
 * @author yusu
 */
@RestController
@RequestMapping("/api/processes")
public class ProcessController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessController.class);
    private static final String DEFAULT_PAGE = "1";
    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final String DEFAULT_VERSION_LIMIT = "100";
    private static final String MAX_PAGE_SIZE = "" + RequestValueParser.MAX_LIST_PAGE_SIZE;
    private static final String DEFAULT_SORT_BY = "updatedAt";
    private static final String DEFAULT_SORT_ORDER = "desc";
    private static final String PUBLICATION_REQUEST_FINGERPRINT = "workbench.publish.requestFingerprint";
    private static final String PUBLICATION_SOURCE_REVISION = "workbench.publish.sourceRevision";
    private static final ProcessImportParser PROCESS_IMPORT_PARSER = new ProcessImportParser();
    private final PublishedProcessExecutionService executionService;
    private final ProcessDefinitionPreflightService preflightService;
    private final ProcessDraftService processDraftService;
    private final ProcessDeploymentService deploymentService;
    private final ServerIdentity identity;

    @Autowired
    public ProcessController(PublishedProcessExecutionService executionService,
            ProcessDefinitionPreflightService preflightService, ProcessDraftService processDraftService,
            ProcessDeploymentService deploymentService, ServerIdentity identity) {
        this.executionService = executionService;
        this.preflightService = preflightService;
        this.processDraftService = processDraftService;
        this.deploymentService = deploymentService;
        this.identity = identity;
    }

    private static ProcessModelType normalizeOptionalProcessType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        return normalizeProcessType(type);
    }

    private static ProcessModelType normalizeProcessType(String type) {
        String normalized = type.trim();
        try {
            return ProcessModelType.valueOf(normalized);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("type must be one of: BPMN, TBBPM", failure);
        }
    }

    private static String normalizeSortBy(String sortBy) {
        String normalized = StringUtils.defaultIfBlank(sortBy, DEFAULT_SORT_BY).trim();
        if ("name".equals(normalized) || "createdAt".equals(normalized) || "updatedAt".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("sortBy must be one of: name, createdAt, updatedAt");
    }

    private static String normalizeSortOrder(String sortOrder) {
        String normalized = StringUtils.defaultIfBlank(sortOrder, DEFAULT_SORT_ORDER).trim();
        if ("asc".equals(normalized) || "desc".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("sortOrder must be one of: asc, desc");
    }

    private static ProcessRef toProcessRef(String code, ExecutionRoutingRequest routing) {
        if (routing == null) {
            throw new IllegalArgumentException("routing must contain exactly one of version or alias");
        }
        routing.validateRouteSelection();
        return routing.version() == null
                ? ProcessRef.alias(ProcessRef.DEFAULT_NAMESPACE, code, routing.alias())
                : ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, code, routing.version());
    }

    private static ProcessExecutionOptions toExecutionOptions(ProcessExecutionRequest request) {
        ExecutionRoutingRequest routing = request.routing();
        routing.validateAliasRouting();
        return ProcessExecutionOptions
            .builder()
            .invocationId(request.invocationId())
            .aliasRouting(new AliasRoutingOptions(routing.routingKey(), routing.attributes()))
            .build();
    }

    private static ProcessDefinitionResponse toProcessResponse(ProcessDraftService.ProcessRecord process) {
        return new ProcessDefinitionResponse(process.code(), process.name(), process.type(), process.xml(),
                process.createdAt(), process.updatedAt(), process.revision(), process.description(), process.tags(),
                process.createdBy());
    }

    private static ProcessSummaryResponse toSummaryResponse(ProcessDraftService.ProcessSummaryRecord process) {
        return new ProcessSummaryResponse(process.code(), process.name(), process.type(), process.createdAt(),
                process.updatedAt(), process.revision(), process.description(), process.tags(), process.createdBy());
    }

    private static ProcessVersionResponse toVersionResponse(PublishedProcessVersion version) {
        return new ProcessVersionResponse(version.getRef().code(), version.getRef().version(), version.getModelType(),
                version.getCreatedAt().toString(), version.getMetadata().get(ReleaseMetadataKeys.CHANGELOG),
                version.getActor());
    }

    private static String publishVersion(String code, String idempotencyKey) {
        return PublicationDigests.version(ProcessRef.DEFAULT_NAMESPACE, code, idempotencyKey);
    }

    private static String publicationRequestFingerprint(String code, long expectedRevision, String changelog) {
        return PublicationDigests.request(code, expectedRevision, StringUtils.defaultString(changelog));
    }

    private static void requireMatchingPublicationRequest(PublishedProcessVersion published, String requestFingerprint) {
        if (requestFingerprint.equals(published.getMetadata().get(PUBLICATION_REQUEST_FINGERPRINT))) {
            return;
        }
        throw DeploymentException.fromRef(DeploymentErrorCode.IDEMPOTENCY_CONFLICT,
                "Idempotency-Key was reused for a different publication request", published.getRef());
    }

    /**
     * Lists processes with optional filtering, sorting, and one-based pagination.
     *
     * @param type      optional process model type filter
     * @param keyword   optional keyword matched against process code, name, and description
     * @param page      one-based page number
     * @param pageSize  maximum number of processes returned on the page
     * @param sortBy    field used for sorting
     * @param sortOrder sort direction
     * @return page containing matching processes and total count metadata
     */
    @GetMapping
    @Parameters({@Parameter(name = "page", in = ParameterIn.QUERY, schema = @Schema(implementation = Integer.class, defaultValue = DEFAULT_PAGE, minimum =
            "1")),
            @Parameter(name = "pageSize", in = ParameterIn.QUERY, schema = @Schema(implementation = Integer.class, defaultValue = DEFAULT_PAGE_SIZE, minimum =
            "1", maximum = MAX_PAGE_SIZE)),
            @Parameter(name = "sortBy", in = ParameterIn.QUERY, schema = @Schema(allowableValues = {"name",
            "createdAt", "updatedAt"}, defaultValue = DEFAULT_SORT_BY)),
            @Parameter(name = "sortOrder", in = ParameterIn.QUERY, schema = @Schema(allowableValues = {"asc", "desc"}, defaultValue = DEFAULT_SORT_ORDER))})
    public ResponseEntity<ProcessListResponse> listProcesses(
            @Parameter(schema = @Schema(allowableValues = {"TBBPM", "BPMN"})) @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword, @RequestParam(defaultValue = DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(defaultValue = DEFAULT_SORT_BY) String sortBy,
            @RequestParam(defaultValue = DEFAULT_SORT_ORDER) String sortOrder) {
        ProcessModelType normalizedType;
        String normalizedSortBy;
        String normalizedSortOrder;
        try {
            normalizedType = normalizeOptionalProcessType(type);
            normalizedSortBy = normalizeSortBy(sortBy);
            normalizedSortOrder = normalizeSortOrder(sortOrder);
            RequestValueParser.requireOneBasedPage(page, pageSize, RequestValueParser.MAX_LIST_PAGE_SIZE);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        ProcessDraftService.ProcessListQuery query = new ProcessDraftService.ProcessListQuery(normalizedType, keyword,
                normalizedSortBy, normalizedSortOrder, page, pageSize);
        ProcessDraftService.ProcessListResult result = processDraftService.listProcesses(query);

        return ResponseEntity.ok(
                new ProcessListResponse(result.data().stream().map(ProcessController::toSummaryResponse).toList(),
                        result.total(), result.page(), result.pageSize()));
    }

    /**
     * Returns a process by code.
     *
     * @param code process code
     * @return process details, or {@code 404} when the process does not exist
     */
    @GetMapping("/{code}")
    public ResponseEntity<ProcessDefinitionResponse> getProcess(@PathVariable String code) {
        ProcessDraftService.ProcessRecord process = processDraftService
            .getProcess(code)
            .orElseThrow(() -> ApiProblemException.notFound("Process '" + code + "' was not found"));
        return ResponseEntity.ok(toProcessResponse(process));
    }

    /**
     * Creates a process.
     *
     * @param body request body containing code, model type, and optional XML metadata
     * @return created process details, or {@code 400} when the request body is invalid
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProcessDefinitionResponse> createProcess(@RequestBody CreateProcessRequest body) {
        if (body == null) {
            throw ApiProblemException.invalidRequest("request body is required");
        }
        String code;
        String name;
        ProcessModelType type;
        String xml;
        String desc;
        List<String> tags;
        try {
            code = body.requireCode();
            name = body.requireName();
            type = body.requireType();
            xml = body.xml();
            desc = body.description();
            tags = body.tags();
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        ProcessDraftService.ProcessCreate create =
                new ProcessDraftService.ProcessCreate(code, name, type, xml, desc, tags);
        ProcessDraftService.ProcessRecord process = processDraftService.createProcess(create);
        return ResponseEntity.created(URI.create("/api/processes/" + process.code())).body(toProcessResponse(process));
    }

    /**
     * Updates editable process metadata and XML.
     *
     * @param code process code
     * @param body full replacement of editable draft fields and expected revision
     * @return updated process details, or {@code 404} when the process does not exist
     */
    @PutMapping("/{code}")
    public ResponseEntity<ProcessDefinitionResponse> updateProcess(@PathVariable String code,
            @RequestBody UpdateProcessRequest body) {
        if (body == null) {
            throw ApiProblemException.invalidRequest("request body is required");
        }
        ProcessDraftService.ProcessUpdate update;
        try {
            update = new ProcessDraftService.ProcessUpdate(body.requireName(), body.requireXml(), body.description(),
                    body.tags(), body.requireExpectedRevision());
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        ProcessDraftService.ProcessRecord process = processDraftService
            .updateProcess(code, update)
            .orElseThrow(() -> ApiProblemException.notFound("Process '" + code + "' was not found"));
        return ResponseEntity.ok(toProcessResponse(process));
    }

    /**
     * Deletes a process.
     *
     * @param code process code
     * @return {@code 204} when deleted or {@code 404} when absent
     */
    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteProcess(@PathVariable String code, @RequestParam long expectedRevision) {
        if (expectedRevision < 0L) {
            throw ApiProblemException.invalidRequest("expectedRevision must be a non-negative integer");
        }
        if (!processDraftService.deleteProcess(code, expectedRevision)) {
            throw ApiProblemException.notFound("Process '" + code + "' was not found");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists published versions for a process.
     *
     * @param code          process code
     * @param versionPrefix optional literal version-prefix filter
     * @param cursor        optional opaque continuation cursor
     * @param limit         maximum versions returned
     * @return newest-first page of immutable published versions
     */
    @GetMapping("/{code}/versions")
    public ResponseEntity<ProcessVersionListResponse> getProcessVersions(@PathVariable String code,
            @RequestParam(required = false) String versionPrefix, @RequestParam(required = false) String cursor,
            @Parameter(schema = @Schema(implementation = Integer.class, defaultValue = DEFAULT_VERSION_LIMIT, minimum =
            "1", maximum = MAX_PAGE_SIZE)) @RequestParam(defaultValue = DEFAULT_VERSION_LIMIT) int limit) {
        PublishedVersionPage result;
        try {
            PublishedVersionQuery query = new PublishedVersionQuery(ProcessRef.DEFAULT_NAMESPACE, code, versionPrefix,
                    StringUtils.isBlank(cursor) ? null : new PublishedVersionCursor(cursor), limit);
            result = deploymentService.listVersions(query);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        List<ProcessVersionResponse> versions =
                result.getVersions().stream().map(ProcessController::toVersionResponse).toList();
        return ResponseEntity.ok(
                new ProcessVersionListResponse(versions,
                        result.getNextCursor() == null ? null : result.getNextCursor().value(), result.hasMore()));
    }

    /**
     * Publishes a new version of a process.
     *
     * @param code           process code
     * @param idempotencyKey retry identity for this publication request
     * @param body           request body containing the expected draft revision and optional changelog
     * @return published version metadata, or {@code 404} when the process does not exist
     */
    @PostMapping("/{code}/publish")
    public ResponseEntity<ProcessVersionResponse> publishProcess(@PathVariable String code,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey, @RequestBody PublishProcessRequest body) {
        if (body == null) {
            throw ApiProblemException.invalidRequest("request body is required");
        }
        long expectedRevision;
        try {
            expectedRevision = body.requireExpectedRevision();
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        String changelog = body.changelog();
        if (StringUtils.isBlank(idempotencyKey)) {
            throw ApiProblemException.invalidRequest("Idempotency-Key is required");
        }
        String publicationKey;
        try {
            publicationKey = RolloutConstraints.requireIdempotencyKey(idempotencyKey);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        String version = publishVersion(code, publicationKey);
        ProcessRef.Version ref = ProcessRef.version(ProcessRef.DEFAULT_NAMESPACE, code, version);
        String requestFingerprint = publicationRequestFingerprint(code, expectedRevision, changelog);
        Optional<PublishedProcessVersion> replay = deploymentService.getVersion(ref);
        if (replay.isPresent()) {
            PublishedProcessVersion published = replay.get();
            requireMatchingPublicationRequest(published, requestFingerprint);
            return ResponseEntity.ok(toVersionResponse(published));
        }
        ProcessDraftService.ProcessRecord process = processDraftService
            .getProcess(code)
            .orElseThrow(() -> ApiProblemException.notFound("Process '" + code + "' was not found"));
        if (process.revision() != expectedRevision) {
            throw ApiProblemException.of(HttpStatus.PRECONDITION_FAILED, "PROCESS_REVISION_CONFLICT",
                    "Process revision conflict",
                    "Process revision mismatch: code=" + code + ", expected=" + expectedRevision + ", current=" + process.revision());
        }
        ProcessPreflightReport preflight = preflightService.preflight(code, process.type(), process.xml());
        if (preflight.getOverallStatus() != ProcessPreflightReport.OverallStatus.PASS) {
            ApiProblemException failure = ApiProblemException.of(HttpStatus.UNPROCESSABLE_CONTENT,
                    "PROCESS_PREFLIGHT_FAILED", "Process preflight failed",
                    "Process preflight failed; no version was published");
            failure.getBody().setProperty("preflight", PreflightReportResponse.from(preflight));
            throw failure;
        }
        ProcessDefinition.Inline definition = ProcessDefinition.inline(process.type(), code, process.xml());
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(PUBLICATION_REQUEST_FINGERPRINT, requestFingerprint);
        metadata.put(PUBLICATION_SOURCE_REVISION, Long.toString(expectedRevision));
        if (StringUtils.isNotBlank(changelog)) {
            metadata.put(ReleaseMetadataKeys.CHANGELOG, changelog);
        }
        PublishedProcessVersion published =
                deploymentService.publish(
                        new PublishProcessVersionCommand(ref, definition, identity.principal(), metadata));
        requireMatchingPublicationRequest(published, requestFingerprint);
        return ResponseEntity.ok(toVersionResponse(published));
    }

    /**
     * Duplicates an existing process into a new code and name.
     *
     * @param code source process code
     * @param body request body containing the new process code and name
     * @return duplicated process details, or {@code 404} when the source process does not exist
     */
    @PostMapping("/{code}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProcessDefinitionResponse> duplicateProcess(@PathVariable String code,
            @RequestBody DuplicateProcessRequest body) {
        if (body == null) {
            throw ApiProblemException.invalidRequest("request body is required");
        }
        String newCode;
        String newName;
        try {
            newCode = body.requireNewCode();
            newName = body.requireNewName();
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        ProcessDraftService.ProcessRecord process = processDraftService
            .duplicateProcess(code, newCode, newName)
            .orElseThrow(() -> ApiProblemException.notFound("Process '" + code + "' was not found"));
        return ResponseEntity.created(URI.create("/api/processes/" + process.code())).body(toProcessResponse(process));
    }

    /**
     * Exports process XML as a downloadable attachment.
     *
     * @param code process code
     * @return XML attachment, or {@code 404} when the process does not exist
     */
    @GetMapping(value = "/{code}/export", produces = MediaType.APPLICATION_XML_VALUE)
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/xml", schema = @Schema(type = "str"
            + "ing", format = "binary")), description = "Raw UTF-8 XML attachment (not base64 encoded)")
    public ResponseEntity<byte[]> exportProcess(@PathVariable String code) {
        ProcessDraftService.ProcessRecord process = processDraftService
            .getProcess(code)
            .orElseThrow(() -> ApiProblemException.notFound("Process '" + code + "' was not found"));
        String xml = process.xml() != null ? process.xml() : "";
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + code + ".xml\"")
            .contentType(MediaType.APPLICATION_XML)
            .body(bytes);
    }

    /**
     * Imports a process from an uploaded XML file.
     *
     * @param file uploaded XML file
     * @return imported process details
     * @throws IOException when the upload cannot be read
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProcessDefinitionResponse> importProcess(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw ApiProblemException.invalidRequest("Imported XML file must not be empty");
        }
        byte[] contentBytes = file.getBytes();
        String content;
        ProcessImportParser.ImportedProcess imported;
        try {
            ProcessImportParser.ImportedDocument document = PROCESS_IMPORT_PARSER.parse(contentBytes);
            imported = document.process();
            content = document.xml();
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        CreateProcessRequest createRequest;
        try {
            String name = imported.name() == null ? imported.code() : imported.name();
            createRequest = new CreateProcessRequest(imported.code(), name, imported.type(), content,
                    "Imported from XML", List.of());
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        ProcessDraftService.ProcessRecord process = processDraftService.createProcess(
                new ProcessDraftService.ProcessCreate(createRequest.requireCode(), createRequest.requireName(),
                        createRequest.requireType(), createRequest.xml(), createRequest.description(),
                        createRequest.tags()));
        return ResponseEntity.created(URI.create("/api/processes/" + process.code())).body(toProcessResponse(process));
    }

    /**
     * Executes a process with optional invocation parameters and routing options.
     *
     * @param code process code
     * @param body optional request body containing params and routing options
     * @return execution result, or an error response when validation or execution fails
     */
    @PostMapping("/{code}/execute")
    public ResponseEntity<ProcessExecutionResponse> executePublishedProcess(@PathVariable String code,
            @RequestBody(required = false) ProcessExecutionRequest body) {
        ProcessExecutionRequest request = body == null ? ProcessExecutionRequest.empty() : body;
        ProcessRef selector;
        ProcessExecutionOptions options;
        try {
            selector = toProcessRef(code, request.routing());
            options = toExecutionOptions(request);
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        try {
            ProcessExecutionResponse result = executionService.execute(selector, request.params(), options);
            return ResponseEntity.ok(result);
        } catch (PublishedProcessExecutionService.InvalidExecutionRequestException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        } catch (DeploymentException failure) {
            throw failure;
        } catch (Exception failure) {
            LOGGER.error("Unexpected Process execution boundary failure: code={}", code,
                    RedactedFailure.forLogging(failure));
            throw ApiProblemException.internalError("Internal execution error");
        }
    }

    /**
     * Validates a process without deploying it.
     *
     * @param body request body containing process code, model type, and XML
     * @return preflight report, or an error response when validation fails
     */
    @PostMapping("/preflight")
    public ResponseEntity<PreflightReportResponse> preflightProcessDefinition(@RequestBody ProcessPreflightRequest body) {
        if (body == null) {
            throw ApiProblemException.invalidRequest("request body is required");
        }
        try {
            body.validate();
        } catch (IllegalArgumentException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        }
        try {
            ProcessPreflightReport report = preflightService.preflight(body.code(), body.modelType(), body.xml());
            return ResponseEntity.ok(PreflightReportResponse.from(report));
        } catch (ProcessDefinitionPreflightService.InvalidProcessDefinitionException failure) {
            throw ApiProblemException.invalidRequest(failure.getMessage());
        } catch (Exception failure) {
            LOGGER.error("Unexpected Process preflight failure: code={}", body.code(),
                    RedactedFailure.forLogging(failure));
            throw ApiProblemException.internalError("Internal preflight error");
        }
    }
}
