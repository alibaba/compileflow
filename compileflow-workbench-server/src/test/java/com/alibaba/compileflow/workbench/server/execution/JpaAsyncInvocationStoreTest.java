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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class JpaAsyncInvocationStoreTest {
    @ParameterizedTest
    @EnumSource(CompileFlowWorkbenchServerProperties.Database.Provider.class)
    void readsAuthorityTimeFromTheSelectedDatabase(CompileFlowWorkbenchServerProperties.Database.Provider provider) {
        CompileFlowWorkbenchServerProperties properties = mock(CompileFlowWorkbenchServerProperties.class);
        CompileFlowWorkbenchServerProperties.Database database =
                mock(CompileFlowWorkbenchServerProperties.Database.class);
        when(properties.getDatabase()).thenReturn(database);
        when(database.getProvider()).thenReturn(provider);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        String sql = provider == CompileFlowWorkbenchServerProperties.Database.Provider.POSTGRESQL
                ? "SELECT CAST(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000 AS BIGINT)"
                : "SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS SIGNED)";
        when(entityManager.createNativeQuery(sql)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(123L);
        AsyncInvocationStore store = new JpaAsyncInvocationStore(mock(AsyncInvocationRepository.class),
                mock(AsyncInvocationAttemptRepository.class), entityManager, properties);

        assertThat(store.currentTimeMillis()).isEqualTo(123L);
    }
}
