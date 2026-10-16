package com.hmong.dbi18n.store;

/**
 * 数据字段国际化运行时开关
 * <p>
 * 开关是展示查询是否改写的唯一运行时依据：缺省关闭；读取失败、值非法都按关闭处理，
 * 保证异常时回到原文展示；写入失败必须抛出，由调用方明确反馈失败。
 *
 * @author yanx
 */
public interface DataTranslationSwitchService
{
    /**
     * 读取开关
     *
     * @return true：启用自动翻译
     */
    boolean isEnabled();

    /**
     * 写入开关
     *
     * @param enabled 是否启用
     */
    void setEnabled(boolean enabled);
}
