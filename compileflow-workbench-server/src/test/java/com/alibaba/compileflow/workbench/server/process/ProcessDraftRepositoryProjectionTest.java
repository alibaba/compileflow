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
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProcessDraftRepositoryProjectionTest {
    @Autowired
    private ProcessDraftRepository repository;

    private static ProcessDraftEntity flow(String code, String updatedAt, String description) {
        ProcessDraftEntity entity = new ProcessDraftEntity();
        entity.setCode(code);
        entity.setName(code);
        entity.setType(ProcessModelType.BPMN);
        entity.setXml("<definitions>" + "x".repeat(1024) + "</definitions>");
        entity.setDescription(description);
        entity.setCreatedAt(Instant.parse("2026-07-25T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse(updatedAt));
        entity.setCreatedBy("integration-test");
        entity.setTagsJson("[]");
        return entity;
    }

    @Test
    void filtersLiteralKeywordsAndProjectsMetadataWithoutDefinitionXml() {
        repository.save(flow("projection.older", "2026-07-25T01:00:00Z", "unrelated"));
        repository.save(flow("projection.newer", "2026-07-25T01:00:00.100Z", "literal 50%_!"));

        Page<ProcessDraftSummaryProjection> page = repository.findSummaries(ProcessModelType.BPMN, "%50!%!_!!%",
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("code"))));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent())
            .singleElement()
            .satisfies(flow -> {
                assertThat(flow.getCode()).isEqualTo("projection.newer");
                assertThat(flow.getUpdatedAt()).isEqualTo(Instant.parse("2026-07-25T01:00:00.100Z"));
            });
    }
}
