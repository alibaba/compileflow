package com.alibaba.compileflow.engine.process.preruntime.loader;

import com.alibaba.compileflow.engine.common.constant.FlowModelType;
import com.alibaba.compileflow.engine.common.extension.annotation.ExtensionRealization;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.FlowStreamSource;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.ResourceFlowStreamSource;
import com.alibaba.compileflow.engine.process.preruntime.converter.impl.parser.model.StringFlowStreamSource;
import org.apache.commons.lang3.StringUtils;

/**
 * @author yusu
 */
@ExtensionRealization()
public class DefaultFlowSourceLoader implements FlowSourceLoader {

    @Override
    public FlowStreamSource loadFlowSource(String code, String content, FlowModelType flowModelType) {
        return StringUtils.isNotEmpty(content) ? loadFlowSourceFromContent(code, content)
                : loadFlowSourceByCode(code, flowModelType);
    }

    private FlowStreamSource loadFlowSourceByCode(String code, FlowModelType flowModelType) {
        String filePath = convertToFilePath(code, flowModelType);
        return ResourceFlowStreamSource.of(code, filePath);
    }

    private FlowStreamSource loadFlowSourceFromContent(String code, String content) {
        return StringFlowStreamSource.of(code, content);
    }

    private String convertToFilePath(String code, FlowModelType flowModelType) {
        String path = code.replace(".", "/");
        return path + getBpmFileSuffix(flowModelType);
    }

    private String getBpmFileSuffix(FlowModelType flowModelType) {
        if (FlowModelType.BPMN.equals(flowModelType)) {
            return ".bpmn20";
        }
        return ".bpm";
    }

}
