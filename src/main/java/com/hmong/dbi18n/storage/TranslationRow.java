package com.hmong.dbi18n.storage;

import java.util.Date;

/**
 * 翻译表行
 * <p>
 * 与 {@code sys_data_translation} 一一对应，供仓储层与维护服务内部使用。
 *
 * @author yanx
 */
public class TranslationRow
{
    private String id;

    private String bizType;

    private String bizId;

    private String fieldCode;

    private String locale;

    private String translatedText;

    private String sourceHash;

    private Long createBy;

    private Date createTime;

    private Long updateBy;

    private Date updateTime;

    private String delFlag;

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

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

    public String getSourceHash()
    {
        return sourceHash;
    }

    public void setSourceHash(String sourceHash)
    {
        this.sourceHash = sourceHash;
    }

    public Long getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(Long createBy)
    {
        this.createBy = createBy;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Long getUpdateBy()
    {
        return updateBy;
    }

    public void setUpdateBy(Long updateBy)
    {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }
}
