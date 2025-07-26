package com.alibaba.compileflow.engine.extension.spec;

import com.alibaba.compileflow.engine.extension.Extension;
import com.alibaba.compileflow.engine.extension.annotation.ExtensionRealization;
import com.alibaba.compileflow.engine.extension.annotation.ExtensionPoint;

/**
 * @author yusu
 */
public class ClassExtensionSpec extends ExtensionSpec {

    public static ExtensionSpec of(ExtensionRealization extensionRealizationAnnotation,
                                   ExtensionPoint extensionPointAnnotation,
                                   Extension extensions) {
        ExtensionSpec extensionSpec = new ClassExtensionSpec();
        extensionSpec.setExtension(extensions);
        extensionSpec.setScenario(extensionRealizationAnnotation.scenario());
        extensionSpec.setPriority(extensionRealizationAnnotation.priority());
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
