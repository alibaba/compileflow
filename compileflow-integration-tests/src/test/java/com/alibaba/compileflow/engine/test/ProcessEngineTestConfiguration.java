package com.alibaba.compileflow.engine.test;

import com.alibaba.compileflow.engine.core.extension.ExtensionInvoker;
import com.alibaba.compileflow.engine.core.extension.ExtensionInvokerImpl;
import com.alibaba.compileflow.engine.core.infrastructure.bean.SpringApplicationContextProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author yusu
 */
@Configuration
@ComponentScan(basePackages = {
        "com.alibaba.compileflow.engine.test.om.activity",
        "com.alibaba.compileflow.engine.test.om.router",
        "com.alibaba.compileflow.engine.test.mock",
        "com.alibaba.compileflow.engine.test.execution",
})
public class ProcessEngineTestConfiguration {

    @Bean
    @ConditionalOnMissingBean(SpringApplicationContextProvider.class)
    public SpringApplicationContextProvider applicationContextProvider() {
        return new SpringApplicationContextProvider();
    }

    @Bean
    @ConditionalOnMissingBean(ExtensionInvoker.class)
    public ExtensionInvoker extensionInvoker() {
        return new ExtensionInvokerImpl();
    }

}
