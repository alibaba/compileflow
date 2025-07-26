package com.alibaba.compileflow.engine.extension.mock;

import com.alibaba.compileflow.engine.extension.annotation.ExtensionRealization;

@ExtensionRealization(priority = 100)
public class MockServiceImpl implements MockService {
    @Override
    public String hello() {
        return "hello";
    }
} 