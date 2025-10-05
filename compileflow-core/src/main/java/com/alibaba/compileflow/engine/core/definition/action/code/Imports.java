package com.alibaba.compileflow.engine.core.definition.action.code;

import com.alibaba.compileflow.engine.core.definition.BaseElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yusu
 */
public class Imports extends BaseElement {

    private final List<String> imports = new ArrayList<>();

    public List<String> getImports() {
        return imports;
    }

    public void addImport(String ip) {
        imports.add(ip);
    }

}
