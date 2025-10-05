package com.alibaba.compileflow.engine.core.extension;


/**
 * @author yusu
 * Created on 2022-02-17
 */
public class ExtensionPointSpec {

    private String code;

    private String desc;

    public static ExtensionPointSpec of(ExtensionPoint extensionPointAnnotation) {
        ExtensionPointSpec extensionPointSpec = new ExtensionPointSpec();
        extensionPointSpec.setCode(extensionPointAnnotation.code());
        extensionPointSpec.setDesc(extensionPointAnnotation.desc());
        return extensionPointSpec;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
