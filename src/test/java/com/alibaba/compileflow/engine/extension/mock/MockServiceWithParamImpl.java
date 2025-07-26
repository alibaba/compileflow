package com.alibaba.compileflow.engine.extension.mock;

import com.alibaba.compileflow.engine.extension.annotation.ExtensionRealization;

@ExtensionRealization(priority = 150)
public class MockServiceWithParamImpl implements MockServiceWithParam {
    @Override
    public String hello(String name) {
        return "hello, " + name;
    }
} 