package com.hmong.dbi18n.core;

/**
 * 请求级数据字段国际化快照的线程持有者
 * <p>
 * 仅由 Web 请求拦截器写入与清理；非 Web 调用（定时任务、MQ 消费、单元测试）没有快照时
 * 按“不翻译”处理，保证导入、同步等后台链路始终读取原文。
 *
 * @author yanx
 */
public final class DataTranslationContextHolder
{
    private static final ThreadLocal<DataTranslationContext> CONTEXT = new ThreadLocal<>();

    private DataTranslationContextHolder()
    {
    }

    public static void set(DataTranslationContext context)
    {
        CONTEXT.set(context);
    }

    public static DataTranslationContext get()
    {
        return CONTEXT.get();
    }

    public static void clear()
    {
        CONTEXT.remove();
    }
}
