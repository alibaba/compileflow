package com.alibaba.compileflow.engine.test.boot;

import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessResult;
import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.config.ProcessEngineConfig;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineAutoConfiguration;
import com.alibaba.compileflow.engine.spring.boot.autoconfigure.ProcessEngineProperties;
import com.alibaba.compileflow.engine.test.ProcessEngineTestConfiguration;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CompileFlow Spring Boot auto-configuration.
 * <p>
 * This test verifies that the ProcessEngine is correctly configured and initialized
 * using properties defined in the application context. It also checks that a simple
 * process can be executed successfully.
 * <p>
 * The test uses the "test" profile to load specific configurations suitable for testing.
 *
 * @author yusu
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {
        ConfigurationPropertiesAutoConfiguration.class,
        ProcessEngineAutoConfiguration.class,
        ProcessEngineTestConfiguration.class
}, properties = {
        "compileflow.model-type=TBBPM",
        "compileflow.parallel-compilation-enabled=true",
        "compileflow.executor.compilation-threads=4",
        "compileflow.executor.execution-threads=8"
})
@ActiveProfiles("test")
public class BootAutoConfigIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ProcessEngineProperties properties;

    @Autowired
    private ProcessEngine processEngine;

    @Test
    void shouldBindPropertiesAndCreateEngine() {
        assertNotNull(applicationContext);
        assertNotNull(properties);
        assertEquals(FlowModelType.TBBPM, properties.getModelType());
        assertTrue(properties.isParallelCompilationEnabled());
        assertEquals(4, properties.getExecutor().getCompilationThreads());
        assertEquals(8, properties.getExecutor().getExecutionThreads());

        assertNotNull(processEngine);

        ProcessEngineConfig config = properties.toProcessEngineConfig();
        assertNotNull(config);
        assertEquals(FlowModelType.TBBPM, config.getModelType());
        assertTrue(config.isParallelCompilationEnabled());
        assertEquals(4, config.getExecutorConfig().getCompilationThreads());
        assertEquals(8, config.getExecutorConfig().getExecutionThreads());
    }

    @Test
    void shouldExecuteSimpleKtvProcess() {
        String code = "bpm.ktv.ktvExample";
        Map<String, Object> context = new HashMap<>();
        context.put("pList", Lists.newArrayList("yusu"));

        ProcessResult<Map<String, Object>> result = processEngine.execute(ProcessSource.fromCode(code), context);
        assertEquals(30, result.getData().get("price"));
    }

}
