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
package com.alibaba.compileflow.workbench.server.security;

import com.alibaba.compileflow.workbench.server.config.CompileFlowWorkbenchServerProperties;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Stable service identity established by the backend authentication boundary.
 *
 * @author yusu
 */
@Component
public final class ServerIdentity {
    private final String principal;

    public ServerIdentity(CompileFlowWorkbenchServerProperties properties) {
        this.principal = Objects.requireNonNull(properties.getAuthentication().getServicePrincipal(), "servicePrincipal");
    }

    public String principal() {
        return principal;
    }
}
