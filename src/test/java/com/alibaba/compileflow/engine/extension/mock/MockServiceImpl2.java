package com.alibaba.compileflow.engine.extension.mock;

import com.alibaba.compileflow.engine.extension.annotation.ExtensionRealization;

@ExtensionRealization(priority = 200)
public class MockServiceImpl2 implements MockService {
    @Override
    public String hello() {
        return "hello2";
    }
} 