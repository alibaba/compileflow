package com.alibaba.compileflow.engine.extension;

import org.junit.Test;
import org.junit.Assert;

/**
 * 测试目的：验证 Plugin 抽象类的基本属性和方法。
 * <p>
 * 测试场景：
 * <ul>
 *   <li>插件属性的 get/set 方法</li>
 *   <li>插件扩展点和实现的注册</li>
 *   <li>插件启用状态判断</li>
 * </ul>
 */
public class PluginTest {
    @Test
    public void testPluginProperties() {
        Plugin plugin = new Plugin() {};
        plugin.setId("id");
        plugin.setName("name");
        plugin.setVersion("1.0");
        plugin.setDescription("desc");
        Assert.assertEquals("id", plugin.getId());
        Assert.assertEquals("name", plugin.getName());
        Assert.assertEquals("1.0", plugin.getVersion());
        Assert.assertEquals("desc", plugin.getDescription());
    }

    @Test
    public void testExtensionRegistration() {
        Plugin plugin = new Plugin() {};
        Assert.assertNotNull(plugin.getExtensionPointClasses());
        Assert.assertNotNull(plugin.getExtensionClasses());
    }

    @Test
    public void testIsEnabled() {
        Plugin plugin = new Plugin() {};
        Assert.assertTrue(plugin.isEnabled(null));
    }
}
