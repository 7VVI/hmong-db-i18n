package com.hmong.dbi18n.storage;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 维护视图使用的源业务记录
 * <p>
 * bizId 为源记录规范主键；label 仅用于人工审核时辅助识别（如层级、组合名称），
 * 不参与翻译定位；sourceValues 为 field_code 到当前原文的映射。
 *
 * @author yanx
 */
public class DataTranslationSourceItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String bizId;

    private String label;

    private Map<String, String> sourceValues = new LinkedHashMap<>();

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

    public Map<String, String> getSourceValues()
    {
        return sourceValues;
    }

    public void setSourceValues(Map<String, String> sourceValues)
    {
        this.sourceValues = sourceValues;
    }

    /**
     * 读取指定字段的原文
     *
     * @param fieldCode 字段标识
     * @return 原文，不存在返回 null
     */
    public String sourceValue(String fieldCode)
    {
        return sourceValues.get(fieldCode);
    }
}
