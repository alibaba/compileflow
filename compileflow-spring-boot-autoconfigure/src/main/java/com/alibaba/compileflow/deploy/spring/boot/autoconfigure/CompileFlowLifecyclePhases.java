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
package com.alibaba.compileflow.deploy.spring.boot.autoconfigure;

import org.springframework.context.SmartLifecycle;

/**
 * Internal lifecycle ordering for deployment components.
 *
 * <p>The phases sit between Spring Boot's web-server start/stop phase ({@code DEFAULT_PHASE -
 * 2048}) and graceful-shutdown phase ({@code DEFAULT_PHASE - 1024}). This lets the application
 * become ready only after deployment infrastructure starts and stops HTTP admission before that
 * infrastructure is torn down.
 *
 * @author yusu
 */
final class CompileFlowLifecyclePhases {
    static final int DEPLOY_DATA_PLANE = SmartLifecycle.DEFAULT_PHASE - 1800;
    static final int DEPLOY_CONTROL_PLANE = SmartLifecycle.DEFAULT_PHASE - 1700;
    static final int DEPLOY_RECONCILIATION = SmartLifecycle.DEFAULT_PHASE - 1600;

    private CompileFlowLifecyclePhases() {
    }
}
