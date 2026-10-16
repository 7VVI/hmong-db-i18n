package com.hmong.dbi18n.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * hmong-db-i18n 配置项
 *
 * @author yanx
 */
@ConfigurationProperties(prefix = "hmong.db-i18n")
public class HmongDbI18nProperties
{
    /** 是否启用本库（关闭后不注册任何 Bean） */
    private boolean enabled = true;

    /** 运行时开关的存储键（Redis 键名，多环境共用同一键即可） */
    private String switchKey = "data_translation:enabled";

    /** 开关存储类型：auto=有 Redis 用 Redis，否则本地静态；redis=强制 Redis；static=强制本地静态 */
    private String store = "auto";

    /** store=static 时的初始值 */
    private boolean staticSwitchEnabled = false;

    /** 是否注册 Web 请求拦截器（冻结开关与请求语言快照） */
    private boolean webEnabled = true;

    /** 是否启用译文维护服务（列表/导出/导入/删除） */
    private boolean maintenanceEnabled = true;

    /**
     * 是否由本库声明 PageHelper 的 PageInterceptor 以固定插件顺序
     * <p>
     * 开启后本库会以 @Order(100) 注册 PageInterceptor，配合使用方 MybatisPlusInterceptor 的
     * 默认顺序（最低优先级）即可保证“翻译早于分页生成 count/data”。使用方已自行管控顺序时保持关闭。
     */
    private boolean pagehelperOrder = false;

    /** 启动时校验并打印插件链顺序 */
    private boolean verifyPluginChain = true;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getSwitchKey()
    {
        return switchKey;
    }

    public void setSwitchKey(String switchKey)
    {
        this.switchKey = switchKey;
    }

    public String getStore()
    {
        return store;
    }

    public void setStore(String store)
    {
        this.store = store;
    }

    public boolean isStaticSwitchEnabled()
    {
        return staticSwitchEnabled;
    }

    public void setStaticSwitchEnabled(boolean staticSwitchEnabled)
    {
        this.staticSwitchEnabled = staticSwitchEnabled;
    }

    public boolean isWebEnabled()
    {
        return webEnabled;
    }

    public void setWebEnabled(boolean webEnabled)
    {
        this.webEnabled = webEnabled;
    }

    public boolean isMaintenanceEnabled()
    {
        return maintenanceEnabled;
    }

    public void setMaintenanceEnabled(boolean maintenanceEnabled)
    {
        this.maintenanceEnabled = maintenanceEnabled;
    }

    public boolean isPagehelperOrder()
    {
        return pagehelperOrder;
    }

    public void setPagehelperOrder(boolean pagehelperOrder)
    {
        this.pagehelperOrder = pagehelperOrder;
    }

    public boolean isVerifyPluginChain()
    {
        return verifyPluginChain;
    }

    public void setVerifyPluginChain(boolean verifyPluginChain)
    {
        this.verifyPluginChain = verifyPluginChain;
    }
}
