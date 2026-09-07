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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.spi.routing.AliasTargeting;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AliasTargetSelectorTest {
    private static final ProcessRef.Alias ALIAS = ProcessRef.alias("default", "order.process", "production");

    @Test
    void reportsCanonicalReasonsWithoutRetainingEvaluationInputs() {
        AliasTargetSelector selector = new AliasTargetSelector(Map.of());
        AliasTargetSelector.Selection stable =
                selector.select(ProcessAliasRoute.stable(ALIAS, "v1", 1L), AliasRoutingOptions.defaults());
        AliasTargetSelector.Selection split =
                selector.select(ProcessAliasRoute.canary(ALIAS, "v1", "v2", 5_000, 2L),
                        new AliasRoutingOptions("user-1"));

        assertThat(stable)
            .isEqualTo(
                    new AliasTargetSelector.Selection(ProcessAliasTarget.STABLE, AliasTargetSelector.Reason.STABLE_ONLY,
                            null));
        assertThat(split.reason()).isEqualTo(AliasTargetSelector.Reason.SPLIT);
        assertThat(split.targetingPolicy()).isNull();
    }

    @Test
    void reportsTargetingOverrideAndFallthrough() {
        ProcessAliasTargetingPolicy policy = policy(context -> context.getAttributes().containsKey("candidate")
                ? Optional.of(ProcessAliasTarget.CANDIDATE)
                : Optional.empty());
        AliasTargetSelector selector = new AliasTargetSelector(Map.of(policy.name(), policy));
        ProcessAliasRoute route =
                ProcessAliasRoute.canary(ALIAS, "v1", "v2", 5_000, new AliasTargeting(policy.name()), 3L);

        AliasTargetSelector.Selection targeted =
                selector.select(route, new AliasRoutingOptions("user-1", Map.of("candidate", "true")));
        AliasTargetSelector.Selection split = selector.select(route, new AliasRoutingOptions("user-1"));

        assertThat(targeted)
            .isEqualTo(
                    new AliasTargetSelector.Selection(ProcessAliasTarget.CANDIDATE, AliasTargetSelector.Reason.TARGETING,
                            policy.name()));
        assertThat(split.reason()).isEqualTo(AliasTargetSelector.Reason.SPLIT);
        assertThat(split.targetingPolicy()).isEqualTo(policy.name());
    }

    @Test
    void targetingFailureSelectsStableWithExplicitAttribution() {
        ProcessAliasTargetingPolicy policy = policy(context -> {
            throw new IllegalStateException("broken policy");
        });
        AliasTargetSelector selector = new AliasTargetSelector(Map.of(policy.name(), policy));
        ProcessAliasRoute route =
                ProcessAliasRoute.canary(ALIAS, "v1", "v2", 9_999, new AliasTargeting(policy.name()), 4L);

        AliasTargetSelector.Selection selection = selector.select(route, new AliasRoutingOptions("candidate-key"));

        assertThat(selection)
            .isEqualTo(
                    new AliasTargetSelector.Selection(ProcessAliasTarget.STABLE,
                            AliasTargetSelector.Reason.TARGETING_ERROR, policy.name()));
    }

    @Test
    void targetingFailureDoesNotLogTheExtensionException() {
        String secret = "secret-DO-NOT-LOG-12345";
        ProcessAliasTargetingPolicy policy = policy(context -> {
            throw new IllegalStateException(secret);
        });
        AliasTargetSelector selector = new AliasTargetSelector(Map.of(policy.name(), policy));
        ProcessAliasRoute route =
                ProcessAliasRoute.canary(ALIAS, "v1", "v2", 9_999, new AliasTargeting(policy.name()), 4L);
        Logger logger = (Logger) LoggerFactory.getLogger(AliasTargetSelector.class);

        synchronized (AliasTargetSelector.class) {
            Level previousLevel = logger.getLevel();
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.ERROR);
            try {
                assertThat(selector.select(route, new AliasRoutingOptions("sensitive-routing-key"))).isNotNull();

                assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .allMatch(message -> !message.contains(secret))
                    .anyMatch(message -> message.contains(IllegalStateException.class.getName()));
                assertThat(appender.list).extracting(ILoggingEvent::getThrowableProxy).containsOnlyNulls();
            } finally {
                logger.detachAppender(appender);
                logger.setLevel(previousLevel);
                appender.stop();
            }
        }
    }

    @Test
    void missingNamedPolicyRemainsAConfigurationFailure() {
        ProcessAliasRoute route =
                ProcessAliasRoute.canary(ALIAS, "v1", "v2", 5_000, new AliasTargeting("enterprise-cohort"), 5L);

        assertThatThrownBy(() -> new AliasTargetSelector(Map.of()).select(route, AliasRoutingOptions.defaults()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Alias targeting policy is unavailable: enterprise-cohort");
    }

    private static ProcessAliasTargetingPolicy policy(TargetingFunction function) {
        return new ProcessAliasTargetingPolicy() {
            @Override
            public String name() {
                return "enterprise-cohort";
            }

            @Override
            public Optional<ProcessAliasTarget> target(
                    com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingContext context) {
                return function.target(context);
            }
        };
    }

    @FunctionalInterface
    private interface TargetingFunction {
        Optional<ProcessAliasTarget> target(
                com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingContext context);
    }
}
