package com.alibaba.compileflow.engine.tbbpm.builder.validator;

import com.alibaba.compileflow.engine.core.builder.validator.AbstractFlowModelValidator;
import com.alibaba.compileflow.engine.core.builder.validator.FlowModelValidator;
import com.alibaba.compileflow.engine.core.builder.validator.ValidateMessage;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import com.alibaba.compileflow.engine.tbbpm.definition.TbbpmModel;

import java.util.List;
import java.util.Optional;

/**
 * @author yusu
 */
@ExtensionRealization()
public class TbbpmModelValidator extends AbstractFlowModelValidator<TbbpmModel>
        implements FlowModelValidator<TbbpmModel> {

    @Override
    public List<ValidateMessage> validate(TbbpmModel flowModel) {
        List<ValidateMessage> validateMessages = super.validate(flowModel);
        return validateMessages;
    }

    @Override
    public boolean support(FlowModelValidatorExtensionContext flowModelValidatorContext) {
        return Optional.of(flowModelValidatorContext).map(FlowModelValidatorExtensionContext::getFlowModel)
                .map(e -> e instanceof TbbpmModel).orElse(false);
    }

}
