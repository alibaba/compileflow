package com.alibaba.compileflow.engine.common.extension.spec;

import com.alibaba.compileflow.engine.common.extension.ExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.annotation.Extension;
import com.alibaba.compileflow.engine.common.extension.annotation.Extensions;

/**
 * @author yusu
 */
public class ClassExtensionSpec extends ExtensionSpec {

    public static ExtensionSpec of(Extensions extensionsAnnotation,
                                   Extension extensionAnnotation,
                                   ExtensionPoint extensions) {
        ExtensionSpec extensionSpec = new ClassExtensionSpec();
        extensionSpec.setExtension(extensions);
        extensionSpec.setScenario(extensionsAnnotation.scenario());
        extensionSpec.setPriority(extensionsAnnotation.priority());
        extensionSpec.setCode(extensionAnnotation.code());
        extensionSpec.setName(extensionAnnotation.name());
        extensionSpec.setDescription(extensionAnnotation.description());
        extensionSpec.setGroup(extensionAnnotation.group());
        extensionSpec.setReducePolicy(extensionAnnotation.reducePolicy());
        return extensionSpec;
    }

    @Override
    public boolean isCollectionFlat() {
        return false;
    }

    @Override
    public boolean isMapFlat() {
        return false;
    }

}
