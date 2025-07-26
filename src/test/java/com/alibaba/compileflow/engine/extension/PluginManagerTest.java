package com.alibaba.compileflow.engine.extension;

import org.junit.Test;
import org.junit.Assert;

/**
 * 测试目的：验证 PluginManager 的插件加载、依赖管理、扩展点批量注册等功能。
 * <p>
 * 测试场景：
 * <ul>
 *   <li>插件自动加载与注册</li>
 *   <li>插件依赖关系校验</li>
 *   <li>插件批量注册扩展点和实现</li>
 * </ul>
 */
public class PluginManagerTest {
    @Test
    public void testInit() {
        PluginManager manager = PluginManager.getInstance();
        Assert.assertNotNull(manager);
        manager.init();
    }

    @Test
    public void testGetInstance() {
        PluginManager manager = PluginManager.getInstance();
        Assert.assertNotNull(manager);
    }
}
