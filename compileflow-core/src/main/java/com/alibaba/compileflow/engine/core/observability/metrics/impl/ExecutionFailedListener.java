/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.compileflow.engine.core.observability.metrics.impl;

import com.alibaba.compileflow.engine.core.event.ProcessCoreEvents;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import com.alibaba.compileflow.engine.core.observability.metrics.AbstractMetricsListener;

/**
 * Fires a deterministic alert log entry for every process execution failure.
 * <p>
 * This listener is designed to be simple, stateless, and fast. It does not perform
 * complex logic like rate limiting or cooldowns. Such responsibilities should be
 * handled by external monitoring and alerting systems (e.g., ELK, Prometheus Alertmanager)
 * that can aggregate and filter these structured logs.
 * <p>
 * Note: Logging is only emitted at DEBUG level for testing; production behavior
 * should be provided by business-side extensions.
 * <p>
 * This listener respects the observability configuration switches and will be disabled
 * if observability is turned off.
 *
 * @author yusu
 */
@ExtensionRealization(priority = 10)
public class ExecutionFailedListener extends AbstractMetricsListener<ProcessCoreEvents.ExecutionFailed> {

    @Override
    public boolean doSupport(ProcessEventExtensionContext context) {
        return context != null && context.getEvent() instanceof ProcessCoreEvents.ExecutionFailed;
    }

}
