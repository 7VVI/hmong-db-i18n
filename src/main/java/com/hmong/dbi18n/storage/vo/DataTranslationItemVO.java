package com.hmong.dbi18n.storage.vo;

import java.io.Serializable;

/**
 * 译文维护/导出行
 * <p>
 * 一行 = 一条源记录的一个可翻译字段在目标语言下的视图；导出后人工审核“译文”列，
 * 再连同“原文摘要”一起提交即可。
 *
 * @author yanx
 */
public class DataTranslationItemVO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 未维护译文 */
    public static final String STATUS_MISSING = "MISSING";

    /** 已有有效译文 */
    public static final String STATUS_TRANSLATED = "TRANSLATED";

    /** 译文已过期（原文变化） */
    public static final String STATUS_STALE = "STALE";

    private String bizType;

    private String bizId;

    /** 审核辅助信息，如层级、组合名称 */
    private String label;

    private String fieldCode;

    private String locale;

    /** 当前原文 */
    private String sourceText;

    /** 当前原文摘要，提交译文时必须回传 */
    private String expectedSourceHash;

    /** 已有译文主键，可空 */
    private String translationId;

    /** 当前译文 */
    private String translatedText;

    private String status;

    private String statusText;

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

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
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

    public String getSourceText()
    {
        return sourceText;
    }

    public void setSourceText(String sourceText)
    {
        this.sourceText = sourceText;
    }

    public String getExpectedSourceHash()
    {
        return expectedSourceHash;
    }

    public void setExpectedSourceHash(String expectedSourceHash)
    {
        this.expectedSourceHash = expectedSourceHash;
    }

    public String getTranslationId()
    {
        return translationId;
    }

    public void setTranslationId(String translationId)
    {
        this.translationId = translationId;
    }

    public String getTranslatedText()
    {
        return translatedText;
    }

    public void setTranslatedText(String translatedText)
    {
        this.translatedText = translatedText;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatusText()
    {
        return statusText;
    }

    public void setStatusText(String statusText)
    {
        this.statusText = statusText;
    }
}
