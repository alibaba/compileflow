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
package com.alibaba.compileflow.durable.spring.boot.autoconfigure.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * PostgreSQL Durable Provider configuration.
 *
 * @author yusu
 */
@ConfigurationProperties(prefix = "compileflow.durable-postgres", ignoreUnknownFields = false)
public final class CompileFlowDurablePostgresProperties {
    /**
     * Whether this runtime may apply the owned PostgreSQL migration.
     */
    private final boolean migrate;

    public CompileFlowDurablePostgresProperties(@DefaultValue("false") boolean migrate) {
        this.migrate = migrate;
    }

    public boolean isMigrate() {
        return migrate;
    }
}
