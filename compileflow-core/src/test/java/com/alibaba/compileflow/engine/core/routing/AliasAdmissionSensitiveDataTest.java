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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.compileflow.engine.AliasRoutingOptions;
import com.alibaba.compileflow.engine.ProcessRef;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasRoute;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AliasAdmissionSensitiveDataTest {
    @Test
    void doesNotWriteRoutingKeyToDebugLogs() {
        LocalRoutingState localRoutingState = new LocalRoutingState();
        ProcessRef.Alias alias = ProcessRef.alias(ProcessRef.DEFAULT_NAMESPACE, "order.process", "production");
        localRoutingState.applyAliasRoute(ProcessAliasRoute.canary(alias, "v1", "v2", 5000, 1L));
        AliasAdmission admission = AliasAdmission.forLocalState(localRoutingState.getAliasRouteState());
        String sensitiveRoutingKey = "customer-secret-routing-key";

        Logger logger = (Logger) LoggerFactory.getLogger(AliasAdmission.class);
        synchronized (AliasAdmission.class) {
            Level previousLevel = logger.getLevel();
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.DEBUG);
            try {
                assertThat(admission.admit(alias, new AliasRoutingOptions(sensitiveRoutingKey))).isNotNull();

                assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(sensitiveRoutingKey));
            } finally {
                logger.detachAppender(appender);
                logger.setLevel(previousLevel);
                appender.stop();
            }
        }
    }
}
