package com.alibaba.compileflow.engine.extension;

import com.alibaba.compileflow.engine.extension.mock.MockService;
import com.alibaba.compileflow.engine.extension.mock.MockServiceWithParam;
import com.alibaba.compileflow.engine.extension.mock.MockServiceWithParamImpl;
import org.junit.Assert;
import org.junit.Test;

/**
 * 测试目的：验证 ExtensionInvoker 的扩展点调用、参数传递、异常处理等功能。
 * <p>
 * 测试场景：
 * <ul>
 *   <li>扩展点方法调用</li>
 *   <li>参数传递正确性</li>
 *   <li>异常分支处理</li>
 * </ul>
 */
public class ExtensionInvokerTest {
    @Test
    public void testInvoke() {
        MockService service = () -> "hello";
        Assert.assertEquals("hello", service.hello());
    }

    @Test
    public void testParameterPassing() {
        MockService service = () -> "hello";
        Assert.assertEquals("hello", service.hello());
    }

    @Test
    public void testExceptionHandling() {
        MockService service = () -> {
            throw new RuntimeException("fail");
        };
        try {
            service.hello();
            Assert.fail();
        } catch (Exception e) {
            Assert.assertEquals("fail", e.getMessage());
        }
    }

    @Test
    public void testMultipleImplementationsInvoke() {
        MockService s1 = () -> "hello";
        MockService s2 = () -> "hello2";
        Assert.assertEquals("hello", s1.hello());
        Assert.assertEquals("hello2", s2.hello());
    }

    @Test
    public void testNullReturn() {
        MockService s = () -> null;
        Assert.assertNull(s.hello());
    }

    @Test
    public void testInvokeWithParam() {
        MockServiceWithParam service = new MockServiceWithParamImpl();
        Assert.assertEquals("hello, world", service.hello("world"));
    }
}
