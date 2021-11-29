package com.alibaba.compileflow.engine.common.extension.spec;

import com.alibaba.compileflow.engine.common.extension.IExtensionPoint;
import com.alibaba.compileflow.engine.common.extension.annotation.Extension;
import com.alibaba.compileflow.engine.common.extension.annotation.ExtensionPoint;

/**
 * @author yusu
 */
public class ClassExtensionSpec extends ExtensionSpec {

    public static ExtensionSpec of(Extension extensionAnnotation,
                                   ExtensionPoint extensionPointAnnotation,
                                   IExtensionPoint extensions) {
        ExtensionSpec extensionSpec = new ClassExtensionSpec();
        extensionSpec.setExtension(extensions);
        extensionSpec.setScenario(extensionAnnotation.scenario());
        extensionSpec.setPriority(extensionAnnotation.priority());
        extensionSpec.setCode(extensionPointAnnotation.code());
        extensionSpec.setName(extensionPointAnnotation.name());
        extensionSpec.setDescription(extensionPointAnnotation.description());
        extensionSpec.setGroup(extensionPointAnnotation.group());
        extensionSpec.setReducePolicy(extensionPointAnnotation.reducePolicy());
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
