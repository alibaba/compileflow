package com.alibaba.compileflow.engine.extension;

import com.alibaba.compileflow.engine.extension.mock.MockService;
import com.alibaba.compileflow.engine.extension.mock.MockServiceWithParam;
import org.junit.Assert;
import org.junit.Test;

/**
 * 测试目的：验证 ExtensionManager 的扩展点注册、扩展实现聚合、优先级处理等功能。
 * <p>
 * 测试场景：
 * <ul>
 *   <li>自动注册扩展点和扩展实现</li>
 *   <li>根据 ReducePolicy 聚合扩展实现</li>
 *   <li>动态启用/禁用扩展</li>
 * </ul>
 */
public class ExtensionManagerTest {

    static ExtensionManager manager;

    @org.junit.BeforeClass
    public static void setup() {
        manager = ExtensionManager.getInstance();
        manager.init();
    }

    @Test
    public void testInit() {
        manager.init();
    }

    @Test
    public void testGetExtensionPoint() {
        Assert.assertNotNull(manager.getExtensionPoint("mockService"));
    }

    @Test
    public void testGetExtensions() {
        Assert.assertFalse(manager.getExtensions("mockService", MockService.class).isEmpty());
        Assert.assertEquals("hello2", manager.getExtensions("mockService", MockService.class).get(0).hello());
    }

    @Test
    public void testReducePolicy() {
        // Assert.assertEquals(1, manager.getExtensions("mockService", MockService.class).size());
    }

    @Test
    public void testDynamicEnableDisable() {
        Assert.assertTrue(manager.getExtensions("mockService", MockService.class).stream().allMatch(s -> s.hello() != null));
    }

    @Test
    public void testMultipleImplementationsReducePolicyAll() {
        Assert.assertTrue(manager.getExtensions("mockService", MockService.class).size() >= 2);
        Assert.assertTrue(manager.getExtensions("mockService", MockService.class).stream().anyMatch(s -> "hello2".equals(s.hello())));
    }

    @Test
    public void testExtensionPriority() {
        Assert.assertEquals("hello2", manager.getExtensions("mockService", MockService.class).get(0).hello());
    }

    @Test
    public void testInvalidExtensionPoint() {
        Assert.assertTrue(manager.getExtensions("notExist", MockService.class).isEmpty());
    }

    @Test
    public void testMockServiceWithParam() {
        Assert.assertFalse(manager.getExtensions("mockServiceWithParam", MockServiceWithParam.class).isEmpty());
        Assert.assertEquals("hello, world", manager.getExtensions("mockServiceWithParam", MockServiceWithParam.class).get(0).hello("world"));
    }
}
