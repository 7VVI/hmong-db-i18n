package com.hmong.dbi18n.storage.dto;

import java.io.Serializable;

/**
 * 译文批量保存项
 * <p>
 * expectedSourceHash 为导出时记录的原文摘要，提交时用于确认译文针对的仍是当前原文，
 * 防止旧译文被直接盖上新的摘要。
 * <p>
 * 本类刻意不绑定具体 Excel 框架：导出/导入时由业务系统自行映射列顺序即可
 * （建议列：业务类型 / 业务主键 / 字段标识 / 语言 / 译文 / 原文摘要 / 原文参考）。
 *
 * @author yanx
 */
public class DataTranslationSaveItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String bizType;

    private String bizId;

    private String fieldCode;

    private String locale;

    private String translatedText;

    private String expectedSourceHash;

    /** 原文参考列，仅用于人工审核，导入时忽略 */
    private String sourceText;

    public String getBizType()
    {
        return bizType;
    }

    public void setBizType(String bizType)
    {
        this.bizType = bizType;
    }

    public String getBizId()
    {
        return bizId;
    }

    public void setBizId(String bizId)
    {
        this.bizId = bizId;
    }

    public String getFieldCode()
    {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode)
    {
        this.fieldCode = fieldCode;
    }

    public String getLocale()
    {
        return locale;
    }

    public void setLocale(String locale)
    {
        this.locale = locale;
    }

    public String getTranslatedText()
    {
        return translatedText;
    }

    public void setTranslatedText(String translatedText)
    {
        this.translatedText = translatedText;
    }

    public String getExpectedSourceHash()
    {
        return expectedSourceHash;
    }

    public void setExpectedSourceHash(String expectedSourceHash)
    {
        this.expectedSourceHash = expectedSourceHash;
    }

    public String getSourceText()
    {
        return sourceText;
    }

    public void setSourceText(String sourceText)
    {
        this.sourceText = sourceText;
    }
}
