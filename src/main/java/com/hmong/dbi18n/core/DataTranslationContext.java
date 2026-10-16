package com.hmong.dbi18n.core;

import java.io.Serializable;
import java.util.Locale;

/**
 * 请求级数据字段国际化快照
 * <p>
 * 由 Web 请求拦截器（或任务侧手动设置）在业务查询之前冻结一次“开关 + 请求语言”，
 * SQL 改写插件只读取本快照，不访问 Redis、数据库或任何 Mapper。同一请求内的
 * count 与数据查询使用同一份快照，避免开关切换途中口径不一致。
 *
 * @author yanx
 */
public class DataTranslationContext implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final boolean enabled;

    private final Locale locale;

    public DataTranslationContext(boolean enabled, Locale locale)
    {
        this.enabled = enabled;
        this.locale = locale;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public Locale getLocale()
    {
        return locale;
    }
}
