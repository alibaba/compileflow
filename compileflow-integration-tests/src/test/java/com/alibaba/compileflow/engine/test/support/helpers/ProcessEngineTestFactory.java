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
package com.alibaba.compileflow.engine.test.support.helpers;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessEngineFactory;
import com.alibaba.compileflow.engine.config.JavaDiagnosticsConfig;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import java.nio.file.Path;

/**
 * Creates test engines with optional generated-source and bytecode export.
 *
 * <p>Set {@value #DEBUG_ENABLED_PROPERTY} to {@code true} for a local test run.
 * Artifacts are written below {@code target/compileflow-debug} unless
 * {@value #DEBUG_OUTPUT_DIRECTORY_PROPERTY} supplies another directory.</p>
 *
 * @author yusu
 */
public final class ProcessEngineTestFactory {
    public static final String DEBUG_ENABLED_PROPERTY = "compileflow.test.java-diagnostics.debug";
    public static final String DEBUG_OUTPUT_DIRECTORY_PROPERTY =
            "compileflow.test.java-diagnostics.debug-output-directory";
    private static final Path DEFAULT_DEBUG_OUTPUT_DIRECTORY = Path.of("target", "compileflow-debug");

    private ProcessEngineTestFactory() {
    }

    public static ProcessEngine create() {
        return ProcessEngineFactory.create(builder().build());
    }

    public static ProcessEngineConfig config() {
        return builder().build();
    }

    public static ProcessEngineConfig.Builder builder() {
        return withTestConfiguration(ProcessEngineConfig.builder());
    }

    public static JavaDiagnosticsConfig javaDiagnosticsConfig() {
        JavaDiagnosticsConfig.Builder builder = JavaDiagnosticsConfig.builder();
        if (!Boolean.getBoolean(DEBUG_ENABLED_PROPERTY)) {
            return builder.build();
        }
        return builder
            .debugSymbols(JavaDiagnosticsConfig.DebugSymbols.FULL)
            .debugOutputDirectory(debugOutputDirectory())
            .debugBytecode(true)
            .build();
    }

    private static ProcessEngineConfig.Builder withTestConfiguration(ProcessEngineConfig.Builder builder) {
        return builder.javaDiagnostics(javaDiagnosticsConfig());
    }

    private static Path debugOutputDirectory() {
        String configured = System.getProperty(DEBUG_OUTPUT_DIRECTORY_PROPERTY);
        return configured == null || configured.isBlank() ? DEFAULT_DEBUG_OUTPUT_DIRECTORY : Path.of(configured);
    }
}
