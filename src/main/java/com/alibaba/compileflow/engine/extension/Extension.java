package com.alibaba.compileflow.engine.extension;

/**
 * 扩展实现的标记接口，所有扩展类需实现本接口。
 * <p>
 * 扩展类通过实现本接口并结合 @ExtensionRealization 注解，
 * 可被 ExtensionManager 自动发现和注册到对应扩展点。
 * <p>
 * 用法说明：
 * <ul>
 *   <li>实现 Extension 接口，并实现扩展点定义的方法。</li>
 *   <li>用 @ExtensionRealization 标记扩展实现类。</li>
 *   <li>扩展类可由插件统一注册，或单独注册。</li>
 * </ul>
 * <p>
 * 与扩展点/插件的关系：
 * <ul>
 *   <li>扩展点（@ExtensionPoint）定义可扩展的接口。</li>
 *   <li>扩展实现（Extension）提供具体实现。</li>
 *   <li>插件（Plugin）可批量注册扩展点和扩展实现。</li>
 * </ul>
 */
public interface Extension {

}
