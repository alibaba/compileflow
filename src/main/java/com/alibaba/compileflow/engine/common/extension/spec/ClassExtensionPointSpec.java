package com.alibaba.compileflow.engine.common.extension.spec;

import com.alibaba.compileflow.engine.common.extension.Extension;
import com.alibaba.compileflow.engine.common.extension.annotation.ExtensionPoint;

/**
 * @author yusu
 */
public class ClassExtensionPointSpec extends ExtensionPointSpec {

    public static ClassExtensionPointSpec of(ExtensionPoint extensionPointAnnotation,
                                             Class<? extends Extension> extensionPointClass) {
        ClassExtensionPointSpec extensionMethod = new ClassExtensionPointSpec();
        extensionMethod.setCode(extensionPointAnnotation.code());
        extensionMethod.setName(extensionPointAnnotation.name());
        extensionMethod.setDescription(extensionPointAnnotation.description());
        extensionMethod.setGroup(extensionPointAnnotation.group());
        extensionMethod.setReducePolicy(extensionPointAnnotation.reducePolicy());
        extensionMethod.setExtensionPointClass(extensionPointClass);
        return extensionMethod;
    }

}
