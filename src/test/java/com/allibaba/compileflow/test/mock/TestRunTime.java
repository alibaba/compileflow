package com.allibaba.compileflow.test.mock;

import com.alibaba.compileflow.engine.definition.tbbpm.TbbpmModel;
import com.alibaba.compileflow.engine.runtime.impl.TbbpmProcessRuntime;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

public class TestRunTime {

    /**
     * 测试代码中的异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void test_process_runtime() {

        TbbpmModel model = Mockito.mock(TbbpmModel.class);
        TbbpmProcessRuntime runtime = TbbpmProcessRuntime.of(model);
        when(runtime.generateTestCode()).thenReturn("hello world");
        System.out.println(runtime.generateTestCode());
        runtime.registerNodeGenerator(null);
    }

}
