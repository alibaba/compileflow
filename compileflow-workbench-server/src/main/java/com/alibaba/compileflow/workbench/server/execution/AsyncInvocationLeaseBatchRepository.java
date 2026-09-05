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

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renews one node-local snapshot with database-owned time and one bounded update.
 *
 * @author yusu
 */
@Repository
class AsyncInvocationLeaseBatchRepository {
    private static final String AUTHORITY_TIME_MILLIS = "CAST(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000 AS BIGINT)";
    private static final long MAX_EPOCH_MILLIS = Long.MAX_VALUE;
    private final EntityManager entityManager;

    AsyncInvocationLeaseBatchRepository(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Transactional
    Set<String> renew(Map<String, String> authorities, String runningStatus, long extensionMillis) {
        Map<String, String> snapshot = Map.copyOf(Objects.requireNonNull(authorities, "authorities"));
        if (snapshot.isEmpty()) {
            return Set.of();
        }
        if (extensionMillis <= 0L) {
            throw new IllegalArgumentException("extensionMillis must be positive");
        }
        String status = Objects.requireNonNull(runningStatus, "runningStatus");
        List<Map.Entry<String, String>> ordered =
                snapshot.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).toList();
        String authoritiesPredicate = authorityPredicate(ordered.size());
        String renewedUntil = "CASE WHEN " + AUTHORITY_TIME_MILLIS + " > " + MAX_EPOCH_MILLIS
                + " - :extensionMillis THEN " + MAX_EPOCH_MILLIS + " ELSE " + AUTHORITY_TIME_MILLIS
                + " + :extensionMillis END";
        Query update = entityManager.createNativeQuery(
                "UPDATE cf_async_invocation SET lease_until = " + renewedUntil + ", updated_at = "
                + AUTHORITY_TIME_MILLIS + " WHERE status = :runningStatus AND lease_until >= " + AUTHORITY_TIME_MILLIS
                + " AND (" + authoritiesPredicate + ")");
        update.setParameter("extensionMillis", extensionMillis);
        update.setParameter("runningStatus", status);
        bindAuthorities(update, ordered);
        update.executeUpdate();

        Query select = entityManager.createNativeQuery(
                "SELECT invocation_id FROM cf_async_invocation WHERE status = " + ":runningStatus AND lease_until >= "
                + AUTHORITY_TIME_MILLIS + " AND (" + authoritiesPredicate + ")");
        select.setParameter("runningStatus", status);
        bindAuthorities(select, ordered);
        LinkedHashSet<String> renewed = new LinkedHashSet<>();
        for (Object value : select.getResultList()) {
            renewed.add((String) value);
        }
        return Set.copyOf(renewed);
    }

    private static String authorityPredicate(int size) {
        List<String> predicates = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            predicates.add("(invocation_id = :invocation" + index + " AND lease_token = :token" + index + ")");
        }
        return String.join(" OR ", predicates);
    }

    private static void bindAuthorities(Query query, List<Map.Entry<String, String>> authorities) {
        for (int index = 0; index < authorities.size(); index++) {
            Map.Entry<String, String> authority = authorities.get(index);
            query.setParameter("invocation" + index, authority.getKey());
            query.setParameter("token" + index, authority.getValue());
        }
    }
}
