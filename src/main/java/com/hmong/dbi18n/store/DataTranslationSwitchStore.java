package com.hmong.dbi18n.store;

/**
 * 数据字段国际化运行时开关的存储后端
 * <p>
 * 默认提供 Redis 实现（多节点共享、可运行时切换）与本地静态实现（无 Redis 场景）。
 * 使用方也可以提供自己的实现 Bean（例如配置中心、数据库参数表），只要有唯一 Bean 即可。
 *
 * @author yanx
 */
public interface DataTranslationSwitchStore
{
    /**
     * 读取开关
     *
     * @return true/false；未设置或不可读时返回 null（调用方按关闭处理）
     */
    Boolean read();

    /**
     * 写入开关
     *
     * @param enabled 是否启用
     * @throws RuntimeException 写入失败时抛出，调用方需明确提示失败而不是成功
     */
    void write(boolean enabled);
}
