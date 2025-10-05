package com.alibaba.compileflow.engine.core.builder;


import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.common.CompileFlowException;
import com.alibaba.compileflow.engine.common.ErrorCode;
import com.alibaba.compileflow.engine.core.builder.compiler.ProcessCompiler;
import com.alibaba.compileflow.engine.core.builder.converter.FlowModelConverter;
import com.alibaba.compileflow.engine.core.builder.generator.ProcessCodeGenerator;
import com.alibaba.compileflow.engine.core.builder.validator.FlowModelValidator;
import com.alibaba.compileflow.engine.core.builder.validator.ValidateMessage;
import com.alibaba.compileflow.engine.core.definition.FlowModel;
import com.alibaba.compileflow.engine.core.extension.ExtensionInvoker;
import com.alibaba.compileflow.engine.core.runtime.AbstractProcessRuntime;
import com.alibaba.compileflow.engine.core.runtime.instance.ProcessInstance;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the end-to-end compilation pipeline for a process definition.
 *
 * <p>This abstract class defines the template for converting a {@link ProcessSource}
 * into a fully compiled and executable {@link AbstractProcessRuntime}. Concrete
 * subclasses for specific standards (like BPMN or TBBPM) provide the implementations
 * for the various components involved in the pipeline.
 *
 * <p>The build pipeline consists of the following steps:</p>
 * <ol>
 *   <li><b>Convert to Model:</b> The raw source (e.g., XML) is parsed into a
 *       structured in-memory object graph ({@link FlowModel}) by a
 *       {@link FlowModelConverter}.</li>
 *   <li><b>Validate Model:</b> The {@link FlowModel} is validated for correctness
 *       and consistency by a {@link FlowModelValidator}.</li>
 *   <li><b>Generate Java Code:</b> The validated model is traversed by a
 *       {@link ProcessCodeGenerator}, which generates the corresponding Java
 *       source code.</li>
 *   <li><b>Compile Java Code:</b> The generated Java source is compiled into
 *       bytecode (a {@code .class} file) by the {@link ProcessCompiler}.</li>
 *   <li><b>Create Runtime:</b> A new {@link AbstractProcessRuntime} is created to
 *       hold the compiled class and the original {@link FlowModel}.</li>
 * </ol>
 *
 * @param <T> The specific type of {@link FlowModel} this builder works with.
 * @author yusu
 */
public abstract class AbstractProcessRuntimeBuilder<T extends FlowModel> implements ProcessRuntimeBuilder {

    public final ProcessCompiler compiler = new ProcessCompiler();

    /**
     * Executes the build pipeline to convert a process source into an executable runtime.
     *
     * @param processSource The source of the process definition.
     * @param classLoader   The ClassLoader to use for compiling and loading the class.
     * @return A fully compiled and initialized {@link AbstractProcessRuntime}.
     */
    @Override
    public AbstractProcessRuntime<T> buildProcessRuntime(ProcessSource processSource,
                                                         ClassLoader classLoader) {
        FlowModelConverter<T> flowModelConverter = getFlowModelConverter();
        T flowModel = flowModelConverter.convertToModel(processSource);

        List<ValidateMessage> validateMessages = ExtensionInvoker.getInstance()
                .invokeFirst(new FlowModelValidator.FlowModelValidatorExtensionContext(flowModel),
                        FlowModelValidator.EXT_VALIDATE_CODE, (FlowModelValidator e) -> e.validate(flowModel));
        if (CollectionUtils.isNotEmpty(validateMessages)) {
            throw new CompileFlowException.BusinessException(
                    ErrorCode.CF_VALIDATION_002,
                    "Validation failed: " + validateMessages.stream()
                            .map(ValidateMessage::getMessage)
                            .collect(Collectors.joining("; "))
            );
        }

        ProcessCodeGenerator codeGenerator = getProcessCodeGenerator(flowModel);
        String javaCode = codeGenerator.generateCode();
        String fullClassName = codeGenerator.getClassFullName();

        Class<? extends ProcessInstance> compiledClass = (Class<? extends ProcessInstance>)
                compiler.compile(fullClassName, javaCode, classLoader);

        AbstractProcessRuntime<T> runtime = getProcessRuntime(flowModel, compiledClass);
        return runtime;
    }

    /**
     * Gets the converter responsible for parsing the source into a {@link FlowModel}.
     *
     * @return The flow model converter.
     */
    protected abstract FlowModelConverter<T> getFlowModelConverter();

    /**
     * Gets the generator responsible for creating Java source code from a {@link FlowModel}.
     *
     * @param flowModel The validated in-memory model of the process.
     * @return The process code generator.
     */
    protected abstract ProcessCodeGenerator getProcessCodeGenerator(T flowModel);

    /**
     * Constructs the final {@link AbstractProcessRuntime} container.
     *
     * @param flowModel     The original in-memory model.
     * @param compiledClass The compiled process class.
     * @return A new runtime instance.
     */
    protected abstract AbstractProcessRuntime<T> getProcessRuntime(T flowModel, Class<? extends
            ProcessInstance> compiledClass);

}
