package com.alibaba.compileflow.engine.runtime;

import com.alibaba.compileflow.engine.process.builder.generator.provider.NodeGeneratorProvider;

/**
 * @author yusu
 */
public interface ProcessCodeGenerator {

    String generateCode();

    String generateCode(boolean useCache);

    String generateTestCode();

    String regenerateCode();

    String getClassFullName();

    NodeGeneratorProvider getNodeGeneratorProvider();

}
