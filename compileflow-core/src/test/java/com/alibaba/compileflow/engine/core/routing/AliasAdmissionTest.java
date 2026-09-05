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
package com.alibaba.compileflow.engine.core.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.CompileFlowException;
import com.alibaba.compileflow.engine.ErrorCode;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AliasAdmissionTest {
    private static AliasSelection admit(AliasAdmission admission, String routingKey) {
        return admission.admit(ProcessRef.alias(ProcessRef.DEFAULT_NAMESPACE, "order.process", "production"),
                new AliasRoutingOptions(routingKey));
    }

    @Test
    void admissionReadsTheConfiguredAuthorityOnce() {
        ProcessRef.Alias alias = ProcessRef.alias("default", "order.process", "production");
        AtomicInteger reads = new AtomicInteger();
        AliasAdmission admission = new AliasAdmission(requested -> {
            reads.incrementAndGet();
            return Optional.of(ProcessAliasRoute.stable(requested, "v9", 9L));
        });

        AliasSelection selection = admission.admit(alias, new AliasRoutingOptions("user-1"));

        assertThat(selection.version().version()).isEqualTo("v9");
        assertThat(selection.aliasRevision()).isEqualTo(9L);
        assertThat(reads).hasValue(1);
    }

    @Test
    void selectsOnlyAfterTheMissHandlerPublishesLocalReadyState() {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        AtomicInteger convergences = new AtomicInteger();
        AliasAdmission admission = AliasAdmission.forLocalState(localRoutingState.getAliasRouteState(), alias -> {
            convergences.incrementAndGet();
            localRoutingState.applyAliasRoute(ProcessAliasRoute.stable(alias, "v1", 1L));
        });

        AliasSelection first = admit(admission, "user-1");

        assertThat(first.version().version()).isEqualTo("v1");
        assertThat(first.aliasRevision()).isEqualTo(1L);
        assertThat(first.target()).isEqualTo(ProcessAliasTarget.STABLE);
        assertThat(admit(admission, "user-1").version().version()).isEqualTo("v1");
        assertThat(convergences).hasValue(1);
    }

    @Test
    void missingAliasFailsClosedAfterConvergenceProducesNoRoute() {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        AliasAdmission admission = AliasAdmission.forLocalState(localRoutingState.getAliasRouteState(), alias -> {});

        assertThatThrownBy(() -> admit(admission, "user-1"))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(ErrorCode.CF_EXEC_011))
            .hasMessageContaining("No serving version route")
            .hasMessageContaining("production");
    }

    @Test
    void convergenceFailureIsNotConvertedToNoRoute() {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        AliasAdmission admission = AliasAdmission.forLocalState(localRoutingState.getAliasRouteState(), alias -> {
            throw new IllegalStateException("alias convergence unavailable");
        });

        assertThatThrownBy(() -> admit(admission, "user-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("alias convergence unavailable");
    }

    @Test
    void newerLocalTombstoneRejectsAnOlderConvergenceResult() {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        AliasAdmission admission = AliasAdmission.forLocalState(localRoutingState.getAliasRouteState(), alias -> {
            localRoutingState.applyAliasTombstone(alias, 6L);
            localRoutingState.applyAliasRoute(ProcessAliasRoute.stable(alias, "v5", 5L));
        });

        assertThatThrownBy(() -> admit(admission, "user-1"))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(ErrorCode.CF_EXEC_011))
            .hasMessageContaining("No serving version route");
        assertThatThrownBy(() -> admit(admission, "user-1"))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(ErrorCode.CF_EXEC_011))
            .hasMessageContaining("No serving version route");
    }

    @Test
    void canonicalRoutingSelectsCandidateByPublishedWeight() {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessRef.Alias routeAlias = ProcessRef.alias("default", "order.process", "production");
        localRoutingState.applyAliasRoute(ProcessAliasRoute.canary(routeAlias, "v1", "v2", 5_000, 3L));
        LocalReadyAliasRouteSource routeSource = LocalReadyAliasRouteSource.from(localRoutingState.getAliasRouteState());
        AliasAdmission admission = new AliasAdmission(routeSource);

        AliasSelection selection = admit(admission, "user-1");

        assertThat(selection.version().version()).isEqualTo("v2");
        assertThat(selection.target()).isEqualTo(ProcessAliasTarget.CANDIDATE);
        assertThat(selection.aliasRevision()).isEqualTo(3L);
    }

    @Test
    void namedTargetingCanOverrideOrDelegateToTheFixedSelector() {
        ProcessRef.Alias alias = ProcessRef.alias("default", "order.process", "production");
        ProcessAliasRoute route = ProcessAliasRoute.canary(alias, "v1", "v2", 5_000,
                new AliasTargeting("enterprise-cohort", Map.of("required-region", "eu")), 4L);
        ProcessAliasTargetingPolicy policy = new ProcessAliasTargetingPolicy() {
            @Override
            public String name() {
                return "enterprise-cohort";
            }

            @Override
            public Optional<ProcessAliasTarget> target(
                    com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingContext context) {
                assertThat(context.getParameters()).containsEntry("required-region", "eu");
                return "eu".equals(context.getAttributes().get("region"))
                        ? Optional.of(ProcessAliasTarget.STABLE)
                        : Optional.empty();
            }
        };
        AliasAdmission admission = new AliasAdmission(ignored -> Optional.of(route), Map.of(policy.name(), policy));

        AliasSelection overridden = admission.admit(alias, new AliasRoutingOptions("user-1", Map.of("region", "eu")));
        AliasSelection delegated = admission.admit(alias, new AliasRoutingOptions("user-1"));

        assertThat(overridden.target()).isEqualTo(ProcessAliasTarget.STABLE);
        assertThat(delegated.target()).isEqualTo(ProcessAliasTarget.CANDIDATE);
    }

    @Test
    void missingNamedTargetingPolicyFailsClosed() {
        ProcessRef.Alias alias = ProcessRef.alias("default", "order.process", "production");
        ProcessAliasRoute route =
                ProcessAliasRoute.canary(alias, "v1", "v2", 5_000, new AliasTargeting("enterprise-cohort"), 4L);
        AliasAdmission admission = new AliasAdmission(ignored -> Optional.of(route));

        assertThatThrownBy(() -> admission.admit(alias, new AliasRoutingOptions("user-1")))
            .isInstanceOfSatisfying(CompileFlowException.class, failure -> assertThat(failure.getErrorCode())
                .isEqualTo(ErrorCode.CF_EXEC_011))
            .hasRootCauseMessage("Alias targeting policy is unavailable: enterprise-cohort");
    }
}
