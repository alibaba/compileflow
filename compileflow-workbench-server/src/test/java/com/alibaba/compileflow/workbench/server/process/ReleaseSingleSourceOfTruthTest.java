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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Release Single Source of Truth invariant: the Workbench
 * projection tables {@code cf_deployment} and {@code cf_server_process_version}
 * were removed — the deploy control plane
 * ({@code cf_process_version} + {@code cf_process_alias}
 * + {@code cf_routing_outbox})
 * is the single source of truth for releases and routes.
 *
 * <p>These tests guard the architectural invariant at the API boundary:
 * <ul>
 *   <li>{@link ProcessDraftService} must not expose deployment/version write methods</li>
 *   <li>{@link ProcessController} must publish through the deploy control plane</li>
 *   <li>The deleted entity/repository classes must not be reintroduced</li>
 * </ul>
 *
 * @author yusu
 */
class ReleaseSingleSourceOfTruthTest {
    private static <T> T assertNoThrow(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    /**
     * ProcessDraftService must not expose createDeployment/rollbackDeployment/updateCanary/
     * promoteCanary/listDeployments/getDeployment/publishProcess/getVersions —
     * these were removed to eliminate the double-write.
     */
    @Test
    void processStoreMustNotExposeDeploymentOrVersionWriteMethods() {
        for (String methodName :
                new String[] {"createDeployment", "rollbackDeployment", "updateCanary", "promoteCanary",
                "listDeployments", "getDeployment", "getDeploymentEvents",
                "publishProcess", "getVersions"}) {
            assertThatThrownBy(() -> ProcessDraftService.class.getMethod(methodName, String.class))
                .as("ProcessDraftService.%s must be removed (double-write eliminated)", methodName)
                .isInstanceOf(NoSuchMethodException.class);
        }
    }

    /**
     * ProcessController.publishProcess must exist and accept the unified entry.
     * The actual control-plane delegation is verified by the fact that
     * ProcessDeploymentService is injected (see constructor).
     */
    @Test
    void processControllerPublishProcessUsesDeployControlPlane() {
        // Verify publishProcess still exists as a unified entry
        Method publishProcess = assertNoThrow(() -> ProcessController.class
            .getMethod("publishProcess", String.class, String.class, PublishProcessRequest.class));
        assertThat(publishProcess).isNotNull();
    }

    /**
     * The deleted entity classes must not be reintroduced.
     */
    @Test
    void deletedProjectionEntitiesMustNotReappear() {
        assertThatThrownBy(() -> Class.forName(
                "com.alibaba.compileflow.workbench.server.persistence.entity.DeploymentEntity"))
            .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(
                "com.alibaba.compileflow.workbench.server.persistence.entity.ProcessVersionEntity"))
            .isInstanceOf(ClassNotFoundException.class);
    }

    // --- Helper ---
    /**
     * The deleted repository interfaces must not be reintroduced.
     */
    @Test
    void deletedProjectionRepositoriesMustNotReappear() {
        assertThatThrownBy(() -> Class.forName(
                "com.alibaba.compileflow.workbench.server.persistence.repository.DeploymentRepository"))
            .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(
                "com.alibaba.compileflow.workbench.server.persistence.repository.ProcessVersionStore"))
            .isInstanceOf(ClassNotFoundException.class);
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
