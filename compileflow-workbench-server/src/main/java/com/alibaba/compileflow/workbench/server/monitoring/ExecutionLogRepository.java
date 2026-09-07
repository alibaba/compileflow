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

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for persisted execution logs.
 *
 * @author yusu
 */
public interface ExecutionLogRepository
        extends JpaRepository<ExecutionLogEntity, String>, JpaSpecificationExecutor<ExecutionLogEntity> {
    @Query("""
        select e
          from ExecutionLogEntity e
         where e.namespace = :namespace
           and e.processCode = :processCode
           and e.routingSource = 'alias'
           and e.routeAlias = :routeAlias
           and e.loggedAt >= :cutoff
         order by e.loggedAt desc, e.id asc
        """)
    List<ExecutionLogEntity> findAliasExecutionsSince(@Param("namespace") String namespace,
            @Param("processCode") String processCode, @Param("routeAlias") String routeAlias,
            @Param("cutoff") long cutoff, Pageable pageable);

    default List<ExecutionLogEntity> findAllLimited(Specification<ExecutionLogEntity> specification, Sort sort,
            int limit) {
        return findBy(specification, query -> query.sortBy(sort).limit(limit).all());
    }

    @Query("""
        select e.id
          from ExecutionLogEntity e
         where e.loggedAt < :cutoff
         order by e.loggedAt asc, e.id asc
        """)
    List<String> findIdsOlderThan(@Param("cutoff") long cutoff, Pageable pageable);

    boolean existsByLoggedAtLessThan(long cutoff);

    @Modifying
    @Query("delete from ExecutionLogEntity e where e.id in :ids")
    int deleteIds(@Param("ids") List<String> ids);

    @Query("""
        select count(e) as totalExecutions,
               sum(case when e.status = 'success' then 1 else 0 end)
                   as successExecutions,
               sum(case when e.status = 'failed' then 1 else 0 end)
                   as failedExecutions,
               sum(e.durationMs) as totalDurationMs
          from ExecutionLogEntity e
         where e.loggedAt >= :startMs
           and e.loggedAt < :endMs
           and e.routingSource in ('alias', 'version')
        """)
    ExecutionAggregateProjection aggregateBetween(@Param("startMs") long startMs, @Param("endMs") long endMs);

    @Query("""
        select e.processCode as processCode,
               count(e) as executionCount,
               sum(e.durationMs) as totalDurationMs,
               sum(case when e.status = 'success' then 1 else 0 end)
                   as successCount
          from ExecutionLogEntity e
         where e.loggedAt >= :startMs
           and e.loggedAt < :endMs
           and e.routingSource in ('alias', 'version')
         group by e.processCode
         order by count(e) desc, e.processCode asc
        """)
    List<ProcessAggregateProjection> aggregateProcessesBetween(@Param("startMs") long startMs,
            @Param("endMs") long endMs, Pageable pageable);

    @Query("""
        select e.processCode as processCode,
               e.errorCode as errorCode,
               e.errorMessage as errorMessage,
               e.namespace as namespace,
               e.requestedVersion as requestedVersion,
               e.effectiveVersion as effectiveVersion,
               e.routingSource as routingSource,
               e.routeAlias as routeAlias,
               e.routeRevision as routeRevision,
               count(e) as occurrenceCount,
               max(e.loggedAt) as lastOccurred
          from ExecutionLogEntity e
         where e.status = 'failed'
           and e.loggedAt >= :startMs
           and e.loggedAt < :endMs
           and e.routingSource in ('alias', 'version')
         group by e.processCode,
                  e.errorCode,
                  e.errorMessage,
                  e.namespace,
                  e.requestedVersion,
                  e.effectiveVersion,
                  e.routingSource,
                  e.routeAlias,
                  e.routeRevision
         order by max(e.loggedAt) desc, e.processCode asc,
                  e.errorCode asc nulls first, e.errorMessage asc nulls first,
                  e.namespace asc nulls first, e.requestedVersion asc nulls first,
                  e.effectiveVersion asc nulls first, e.routingSource asc,
                  e.routeAlias asc nulls first, e.routeRevision asc nulls first
        """)
    List<ErrorAggregateProjection> aggregateErrorsBetween(@Param("startMs") long startMs, @Param("endMs") long endMs,
            Pageable pageable);

    @Query("""
        select e.processCode as processCode,
               e.namespace as namespace,
               e.effectiveVersion as effectiveVersion,
               e.routingSource as routingSource,
               e.routeAlias as routeAlias,
               count(e) as executionCount,
               sum(case when e.status = 'success' then 1 else 0 end)
                   as successCount,
               sum(case when e.status = 'failed' then 1 else 0 end)
                   as failedCount,
               sum(e.durationMs) as totalDurationMs
          from ExecutionLogEntity e
         where e.loggedAt >= :startMs
           and e.loggedAt < :endMs
           and e.routingSource in ('alias', 'version')
           and (:processCode is null or e.processCode = :processCode)
         group by e.processCode,
                  e.namespace,
                  e.effectiveVersion,
                  e.routingSource,
                  e.routeAlias
         order by count(e) desc,
                  e.processCode asc,
                  e.effectiveVersion asc nulls first,
                  e.namespace asc nulls first,
                  e.routingSource asc,
                  e.routeAlias asc nulls first
        """)
    List<VersionAggregateProjection> aggregateVersionsBetween(@Param("processCode") String processCode,
            @Param("startMs") long startMs, @Param("endMs") long endMs, Pageable pageable);

    @Query(value = """
        select bucket_index as "bucketIndex",
               count(*) as "executionCount",
               sum(case when status = 'success' then 1 else 0 end)
                   as "successCount",
               sum(case when status = 'failed' then 1 else 0 end)
                   as "failedCount"
          from (
                select floor((logged_at - :startMs) * 1.0 / :bucketMs)
                           as bucket_index,
                       status
                 from cf_execution_log
                 where logged_at >= :startMs
                   and logged_at < :endMs
                   and routing_source in ('alias', 'version')
               ) bucketed
         group by bucket_index
         order by bucket_index
        """, nativeQuery = true)
    List<TrendAggregateProjection> aggregateTrendsBetween(@Param("startMs") long startMs, @Param("endMs") long endMs,
            @Param("bucketMs") long bucketMs);

    interface ExecutionAggregateProjection {
        Long getTotalExecutions();

        Long getSuccessExecutions();

        Long getFailedExecutions();

        Long getTotalDurationMs();
    }

    interface ProcessAggregateProjection {
        String getProcessCode();

        Long getExecutionCount();

        Long getTotalDurationMs();

        Long getSuccessCount();
    }

    interface ErrorAggregateProjection {
        String getProcessCode();

        String getErrorCode();

        String getErrorMessage();

        String getNamespace();

        String getRequestedVersion();

        String getEffectiveVersion();

        String getRoutingSource();

        String getRouteAlias();

        Long getRouteRevision();

        Long getOccurrenceCount();

        Long getLastOccurred();
    }

    interface VersionAggregateProjection {
        String getProcessCode();

        String getNamespace();

        String getEffectiveVersion();

        String getRoutingSource();

        String getRouteAlias();

        Long getExecutionCount();

        Long getSuccessCount();

        Long getFailedCount();

        Long getTotalDurationMs();
    }

    interface TrendAggregateProjection {
        Long getBucketIndex();

        Long getExecutionCount();

        Long getSuccessCount();

        Long getFailedCount();
    }
}
