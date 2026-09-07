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

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.workbench.server.security.ServerIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Application service for editable process-draft persistence.
 *
 * <p>The service owns draft CRUD and optimistic-concurrency rules while delegating
 * collection persistence to {@link ProcessDraftRepository}. Release and route facts live in
 * the deploy control plane; the UI reads those facts without duplicating their ownership.
 *
 * @author yusu
 */
@Service
public class ProcessDraftService {
    private static final TypeReference<List<String>> TAG_LIST_TYPE = new TypeReference<>() {
    };
    private static final ObjectMapper TAG_MAPPER = JsonMapper
        .builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();
    private final ProcessDraftRepository processRepository;
    private final ServerIdentity identity;

    public ProcessDraftService(ProcessDraftRepository processRepository, ServerIdentity identity) {
        this.processRepository = processRepository;
        this.identity = identity;
    }

    private static void requireRevision(String code, long expectedRevision, long currentRevision) {
        if (expectedRevision != currentRevision) {
            throw new ProcessRevisionConflictException(code, expectedRevision, currentRevision);
        }
    }

    private static String keywordPattern(String keyword) {
        String normalized = StringUtils.trimToNull(keyword);
        if (normalized == null) {
            return null;
        }
        String escaped = normalized
            .toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
        return "%" + escaped + "%";
    }

    public ProcessListResult listProcesses(ProcessListQuery query) {
        int page = query.page();
        int pageSize = query.pageSize();
        Sort.Direction direction = "desc".equals(query.sortOrder()) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, query.sortBy()).and(Sort.by(Sort.Direction.ASC, "code"));
        Page<ProcessDraftSummaryProjection> result = processRepository.findSummaries(query.type(),
                keywordPattern(query.keyword()), PageRequest.of(page - 1, pageSize, sort));
        List<ProcessSummaryRecord> data = result.getContent().stream().map(this::toSummaryRecord).toList();
        return new ProcessListResult(data, result.getTotalElements(), page, pageSize);
    }

    public Optional<ProcessRecord> getProcess(String code) {
        return processRepository.findById(code).map(this::toRecord);
    }

    @Transactional
    public ProcessRecord createProcess(ProcessCreate request) {
        Instant now = currentTimestamp();
        ProcessDraftEntity entity = new ProcessDraftEntity();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setXml(request.xml() != null ? request.xml() : "");
        entity.setDescription(request.description());
        entity.setTagsJson(writeTags(request.tags()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(identity.principal());
        persistNewProcess(request.code(), entity);
        return toRecord(entity);
    }

    @Transactional
    public Optional<ProcessRecord> updateProcess(String code, ProcessUpdate request) {
        return processRepository.findById(code).map(existing -> {
            requireRevision(code, request.expectedRevision(), existing.getRevision());
            existing.setName(request.name());
            existing.setXml(request.xml());
            existing.setDescription(request.description());
            existing.setTagsJson(writeTags(request.tags()));
            existing.setUpdatedAt(currentTimestamp());
            processRepository.saveAndFlush(existing);
            return toRecord(existing);
        });
    }

    @Transactional
    public boolean deleteProcess(String code, long expectedRevision) {
        ProcessDraftEntity existing = processRepository.findById(code).orElse(null);
        if (existing == null) {
            return false;
        }
        requireRevision(code, expectedRevision, existing.getRevision());
        processRepository.delete(existing);
        processRepository.flush();
        return true;
    }

    @Transactional
    public Optional<ProcessRecord> duplicateProcess(String code, String newCode, String newName) {
        return processRepository.findById(code).map(source -> {
            Instant now = currentTimestamp();
            ProcessDraftEntity duplicate = new ProcessDraftEntity();
            duplicate.setCode(newCode);
            duplicate.setName(newName);
            duplicate.setType(source.getType());
            duplicate.setXml(new ProcessImportParser()
                .copyWithIdentity(source.getXml(), source.getType(), newCode, newName));
            duplicate.setDescription(source.getDescription());
            duplicate.setTagsJson(source.getTagsJson());
            duplicate.setCreatedAt(now);
            duplicate.setUpdatedAt(now);
            duplicate.setCreatedBy(identity.principal());
            persistNewProcess(newCode, duplicate);
            return toRecord(duplicate);
        });
    }

    private void persistNewProcess(String code, ProcessDraftEntity entity) {
        try {
            processRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException failure) {
            if (failure.getCause() instanceof ConstraintViolationException constraint
                    && constraint.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                throw new ProcessAlreadyExistsException(code, failure);
            }
            throw failure;
        }
    }

    private Instant currentTimestamp() {
        return Objects.requireNonNull(processRepository.currentTimestamp(), "Database returned null current timestamp");
    }

    private ProcessRecord toRecord(ProcessDraftEntity entity) {
        return new ProcessRecord(entity.getCode(), entity.getName(), entity.getType(), entity.getXml(),
                entity.getDescription(), readTags(entity.getTagsJson()), entity.getCreatedAt().toString(),
                entity.getUpdatedAt().toString(), entity.getCreatedBy(), entity.getRevision());
    }

    private ProcessSummaryRecord toSummaryRecord(ProcessDraftSummaryProjection projection) {
        return new ProcessSummaryRecord(projection.getCode(), projection.getName(), projection.getType(),
                projection.getDescription(), readTags(projection.getTagsJson()), projection.getCreatedAt().toString(),
                projection.getUpdatedAt().toString(), projection.getCreatedBy(), projection.getRevision());
    }

    private String writeTags(List<String> tags) {
        try {
            return TAG_MAPPER.writeValueAsString(tags == null ? List.of() : tags);
        } catch (JacksonException failure) {
            throw new IllegalStateException("Failed to serialize process tags", failure);
        }
    }

    private List<String> readTags(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Persisted process tags must be a JSON array");
        }
        List<String> tags;
        try {
            tags = TAG_MAPPER.readValue(json, TAG_LIST_TYPE);
        } catch (JacksonException failure) {
            throw new IllegalStateException("Failed to deserialize persisted process tags", failure);
        }
        if (tags == null || tags.stream().anyMatch(Objects::isNull)) {
            throw new IllegalStateException("Persisted process tags must be a JSON string array");
        }
        return List.copyOf(tags);
    }

    record ProcessRecord(String code, String name, ProcessModelType type, String xml, String description,
            List<String> tags, String createdAt, String updatedAt, String createdBy, long revision) {}

    record ProcessSummaryRecord(String code, String name, ProcessModelType type, String description, List<String> tags,
            String createdAt, String updatedAt, String createdBy, long revision) {}

    record ProcessListQuery(ProcessModelType type, String keyword, String sortBy, String sortOrder, int page,
            int pageSize) {}

    record ProcessListResult(List<ProcessSummaryRecord> data, long total, int page, int pageSize) {}

    record ProcessCreate(String code, String name, ProcessModelType type, String xml, String description,
            List<String> tags) {}

    record ProcessUpdate(String name, String xml, String description, List<String> tags, long expectedRevision) {}
}
