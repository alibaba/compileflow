package com.alibaba.compileflow.engine.extension;

import com.alibaba.compileflow.engine.extension.mock.MockService;
import org.junit.Assert;
import org.junit.Test;

/**
 * 测试目的：验证 Extension 标记接口的实现和扩展点集成。
 * <p>
 * 测试场景：
 * <ul>
 *   <li>扩展实现类是否正确实现 Extension 接口</li>
 *   <li>扩展实现能否被 ExtensionManager 注册和发现</li>
 * </ul>
 */
public class ExtensionTest {
    @Test
    public void testExtensionImplementation() {
        MockService service = () -> "hello";
        Assert.assertEquals("hello", service.hello());
    }

    @Test
    public void testExtensionRegistration() {
        ExtensionManager manager = ExtensionManager.getInstance();
        manager.init();
        Assert.assertTrue(manager.getExtensions("mockService", MockService.class).size() > 0);
    }

    @Test
    public void testMultipleImplementations() {
        ExtensionManager manager = ExtensionManager.getInstance();
        manager.init();
        Assert.assertTrue(manager.getExtensions("mockService", MockService.class).size() >= 2);
    }

    @Test
    public void testPriorityOrder() {
        ExtensionManager manager = ExtensionManager.getInstance();
        manager.init();
        Assert.assertEquals("hello2", manager.getExtensions("mockService", MockService.class).get(0).hello());
    }
}
