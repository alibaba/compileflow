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
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for process drafts.
 *
 * @author yusu
 */
public interface ProcessDraftRepository extends JpaRepository<ProcessDraftEntity, String> {
    @Query("select instant")
    Instant currentTimestamp();

    @Query(value = """
        select f.code as code,
               f.name as name,
               f.type as type,
               f.description as description,
               f.createdAt as createdAt,
               f.updatedAt as updatedAt,
               f.createdBy as createdBy,
               f.tagsJson as tagsJson,
               f.revision as revision
          from ProcessDraftEntity f
         where (:type is null or f.type = :type)
           and (:keywordPattern is null
                or lower(f.code) like :keywordPattern escape '!'
                or lower(f.name) like :keywordPattern escape '!'
                or lower(f.description) like :keywordPattern escape '!')
        """, countQuery = """
        select count(f)
          from ProcessDraftEntity f
         where (:type is null or f.type = :type)
           and (:keywordPattern is null
                or lower(f.code) like :keywordPattern escape '!'
                or lower(f.name) like :keywordPattern escape '!'
                or lower(f.description) like :keywordPattern escape '!')
        """)
    Page<ProcessDraftSummaryProjection> findSummaries(@Param("type") ProcessModelType type,
            @Param("keywordPattern") String keywordPattern, Pageable pageable);
}
