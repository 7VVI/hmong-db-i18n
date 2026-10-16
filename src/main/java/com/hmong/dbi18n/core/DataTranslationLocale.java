package com.hmong.dbi18n.core;

import java.io.Serializable;

/**
 * 一次请求的译文语言候选
 * <p>
 * 精确语言（如 en-US）优先，其次基础语言（如 en），都没命中时回退原文。
 * 请求语言为业务源语言时两个候选都为空，属于“原文模式”，不改写 SQL。
 *
 * @author yanx
 */
public class DataTranslationLocale implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String exact;

    private final String base;

    public DataTranslationLocale(String exact, String base)
    {
        this.exact = exact;
        this.base = base;
    }

    public String getExact()
    {
        return exact;
    }

    public String getBase()
    {
        return base;
    }

    /**
     * 是否为源语言请求（直接使用原文，不需要任何译文关联）
     *
     * @return true：原文模式
     */
    public boolean isSourceLanguage()
    {
        return exact == null && base == null;
    }
}
