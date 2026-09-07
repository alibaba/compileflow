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
package com.alibaba.compileflow.examples.springboot;

import com.alibaba.compileflow.engine.ProcessModelType;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightOptions;
import com.alibaba.compileflow.engine.preflight.ProcessPreflightReport;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Minimal Spring Boot sample that preflights and executes a classpath TBBPM flow.
 *
 * @author yusu
 */
@SpringBootApplication
public class SampleApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(SampleApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    @Bean
    CommandLineRunner runSample(ProcessEngine processEngine) {
        return args -> {
            ProcessDefinition source =
                    ProcessDefinition.classpath(ProcessModelType.TBBPM, "bpm.sample.hello", "flows/hello.bpm");

            ProcessPreflightReport report = processEngine
                .tooling()
                .preflight(source, ProcessPreflightOptions.strict());
            if (report.getOverallStatus() != ProcessPreflightReport.OverallStatus.PASS) {
                throw new IllegalStateException("preflight failed: " + report);
            }

            Map<String, Object> input = new HashMap<>();
            input.put("value", 40);
            ProcessResult<Map<String, Object>> result = processEngine.execute(source, input);
            Integer output = (Integer) result.orElseThrow().get("result");
            LOGGER.info("Sample process completed: result={}", output);
        };
    }
}
