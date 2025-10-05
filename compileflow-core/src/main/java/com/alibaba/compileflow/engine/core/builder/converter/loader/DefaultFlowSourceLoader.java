package com.alibaba.compileflow.engine.core.builder.converter.loader;

import com.alibaba.compileflow.engine.ProcessSource;
import com.alibaba.compileflow.engine.ResourceLocator;
import com.alibaba.compileflow.engine.common.FlowModelType;
import com.alibaba.compileflow.engine.core.builder.converter.parser.model.*;
import com.alibaba.compileflow.engine.core.extension.ExtensionRealization;
import org.apache.commons.lang3.StringUtils;

import java.io.File;

/**
 * A flexible FlowSourceLoader that supports multiple location schemes and
 * configurable mapping rules while remaining backward compatible with the
 * default behavior (code -> path by replacing '.' with '/' + suffix).
 * <p>
 * Supported schemes:
 * - classpath:, cp:   → classpath resource
 * - file:             → file system path
 * - http(s)://, url:  → URL resource
 * - plain code        → mapped by replacing '.' with '/' (optionally with base path)
 * <p>
 *
 * @author yusu
 */
@ExtensionRealization(priority = 800)
public class DefaultFlowSourceLoader implements FlowSourceLoader {

    @Override
    public FlowStreamSource loadFlowSource(ProcessSource processSource, FlowModelType flowModelType) {
        String code = processSource.getCode();
        String content = processSource.getContent();

        if (StringUtils.isNotEmpty(content)) {
            return StringFlowStreamSource.of(code, content);
        }

        ResourceLocator locator = processSource.getLocator();
        if (locator != null) {
            return loadFlowSourceByLocator(code, locator, flowModelType);
        }

        return loadFlowSourceByCode(code, flowModelType);
    }

    private FlowStreamSource loadFlowSourceByLocator(String code, ResourceLocator locator, FlowModelType flowModelType) {
        String scheme = locator.getScheme();
        String path = locator.getPath();

        switch (scheme) {
            case "file":
                return FileFlowStreamSource.of(code, new File(path));
            case "classpath":
                return ResourceFlowStreamSource.of(code, path);
            case "url":
                ResourceLocator.UrlLocator urlLocator = (ResourceLocator.UrlLocator) locator;
                return UrlFlowStreamSource.of(code, urlLocator.getUrl());
            default:
                throw new IllegalArgumentException("Unsupported ResourceLocator scheme: " + scheme);
        }
    }

    private FlowStreamSource loadFlowSourceByCode(String code, FlowModelType flowModelType) {
        String filePath = convertToFilePath(code, flowModelType);
        return ResourceFlowStreamSource.of(code, filePath);
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
